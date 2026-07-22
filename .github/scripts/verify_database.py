#!/usr/bin/env python3
"""Verify privacyguides database entries by downloading APKs and checking fingerprints."""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request

import yaml

DIRECT_SOURCE = "Direct APK Link"
FDROID_SOURCES = {"F-Droid", "F-Droid (IzzyOnDroid)"}
GOOGLE_PLAY_SOURCE = "Google Play"
FDROID_OFFICIAL = "https://f-droid.org/repo"
IZZY_URL = "https://apt.izzysoft.de/fdroid/repo"
FDROID_REPOS = {
    "F-Droid": FDROID_OFFICIAL,
    "F-Droid (IzzyOnDroid)": IZZY_URL,
}

FP_RE = re.compile(r"[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}")
RAW_HEX_RE = re.compile(r"certificate (?:SHA-256|SHA256) digest:\s*([0-9A-Fa-f]{64})")


class ExtractionError(Exception):
    def __init__(self, apksigner_out, apksigner_err, apksigner_rc,
                 keytool_out, keytool_err):
        parts = []
        detail = (apksigner_out + "\n" + apksigner_err).strip()
        if detail:
            parts.append(f"apksigner rc={apksigner_rc}: {detail[:400]}")
        if keytool_err:
            parts.append(f"keytool: {keytool_err[:200]}")
        super().__init__("; ".join(parts) if parts else "no signing block found")


def load_data_yml(path):
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if isinstance(data, dict):
        return data.get("packages", [])
    return data


def normalize_fp(fp):
    fp = re.sub(r"\s+", "", fp).upper().replace(":", "")
    return ":".join(fp[i : i + 2] for i in range(0, len(fp), 2))

def split_fingerprints(fp):
    """Split a concatenated multi-signer fingerprint into individual 32-byte chunks."""
    clean = re.sub(r"\s+", "", fp).replace(":", "")
    chunk_len = 64  # 32 bytes = 64 hex chars
    chunks = []
    for i in range(0, len(clean), chunk_len):
        chunk = clean[i : i + chunk_len]
        if len(chunk) == chunk_len:
            chunks.append(":".join(chunk[j : j + 2] for j in range(0, chunk_len, 2)))
    return chunks


def _gh_token():
    for var in ("GH_TOKEN", "GITHUB_TOKEN"):
        val = os.environ.get(var, "")
        if val:
            return val
    return None


def _is_github_url(url):
    return "github.com" in url and "releases" in url


def download(url, dest, timeout=60):
    token = _gh_token() if _is_github_url(url) else None
    headers = {"User-Agent": "AppVerifierBG-verify/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status != 200:
                return False, f"HTTP {resp.status}"
            with open(dest, "wb") as f:
                f.write(resp.read())
    except Exception as e:
        return _download_curl(url, dest, timeout)
    return True, None


def _download_curl(url, dest, timeout):
    token = _gh_token() if _is_github_url(url) else None
    cmd = ["curl", "-fsSL", "--retry", "3", "--retry-delay", "2",
           "--max-time", str(timeout), "-o", dest]
    if token:
        cmd.extend(["-H", f"Authorization: Bearer {token}"])
    cmd.append(url)
    try:
        subprocess.run(cmd, capture_output=True, timeout=timeout + 30)
        if os.path.getsize(dest) > 0:
            return True, None
        return False, "curl download returned empty file"
    except Exception as e:
        return False, str(e)


def _is_valid_apk(path):
    try:
        result = subprocess.run(
            ["file", path], capture_output=True, text=True, timeout=10
        )
        output = result.stdout.lower()
        if "zip archive" in output or "android" in output or "java archive" in output:
            return True
        return False
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return True


def _find_apksigner():
    for root_var in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        sdk_root = os.environ.get(root_var)
        if not sdk_root:
            continue
        bt_dir = os.path.join(sdk_root, "build-tools")
        if not os.path.isdir(bt_dir):
            continue
        for ver in sorted(os.listdir(bt_dir), reverse=True):
            apksigner = os.path.join(bt_dir, ver, "apksigner")
            if os.path.isfile(apksigner) and os.access(apksigner, os.X_OK):
                return apksigner
    return None


def _find_aapt():
    for root_var in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        sdk_root = os.environ.get(root_var)
        if not sdk_root:
            continue
        bt_dir = os.path.join(sdk_root, "build-tools")
        if not os.path.isdir(bt_dir):
            continue
        for ver in sorted(os.listdir(bt_dir), reverse=True):
            for name in ("aapt2", "aapt"):
                aapt = os.path.join(bt_dir, ver, name)
                if os.path.isfile(aapt) and os.access(aapt, os.X_OK):
                    return aapt
    return None


def extract_package_name(apk_path):
    """Extract the package name from an APK's manifest via aapt dump badging."""
    aapt_path = _find_aapt()
    if not aapt_path:
        return None
    try:
        result = subprocess.run(
            [aapt_path, "dump", "badging", apk_path],
            capture_output=True,
            text=True,
            timeout=15,
        )
        m = re.search(r"package:\s+name='([^']+)'", result.stdout)
        if m:
            return m.group(1)
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return None


def is_valid_package_name(name):
    """Check if a string looks like a valid Android package name."""
    return bool(re.match(r'^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$', name))


def _search_output(output):
    m = FP_RE.search(output)
    if m:
        return normalize_fp(m.group(0))
    m = RAW_HEX_RE.search(output)
    if m:
        return normalize_fp(m.group(1))
    return None


def extract_fingerprint(apk_path):
    if not os.path.getsize(apk_path):
        return None

    apksigner_path = _find_apksigner() or "apksigner"
    apksigner_out = ""
    apksigner_err = ""
    apksigner_rc = -1
    try:
        result = subprocess.run(
            [apksigner_path, "verify", "--print-certs", apk_path],
            capture_output=True,
            text=True,
            timeout=15,
        )
        apksigner_out = result.stdout.strip()
        apksigner_err = result.stderr.strip()
        apksigner_rc = result.returncode
        fp = _search_output(apksigner_out) or _search_output(apksigner_err)
        if fp:
            return fp
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        apksigner_err = str(e)

    keytool_out = ""
    keytool_err = ""
    try:
        result = subprocess.run(
            ["keytool", "-printcert", "-jarfile", apk_path],
            capture_output=True,
            text=True,
            timeout=15,
        )
        keytool_out = result.stdout.strip()
        keytool_err = result.stderr.strip()
        fp = _search_output(keytool_out) or _search_output(keytool_err)
        if fp:
            return fp
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        keytool_err = str(e)

    # fallback: extract JAR signature manually
    for cert_file in ("META-INF/CERT.RSA", "META-INF/CERT.EC"):
        try:
            result = subprocess.run(
                ["unzip", "-p", apk_path, cert_file],
                capture_output=True,
                timeout=15,
            )
            if result.returncode == 0 and result.stdout:
                cert_result = subprocess.run(
                    ["keytool", "-printcert", "-file", "-"],
                    input=result.stdout,
                    capture_output=True,
                    timeout=15,
                )
                fp = _search_output(cert_result.stdout.decode())
                if fp:
                    return fp
        except (subprocess.TimeoutExpired, FileNotFoundError):
            pass

    raise ExtractionError(apksigner_out, apksigner_err, apksigner_rc,
                          keytool_out, keytool_err)


def fetch_fdroid_index(repo_url):
    try:
        req = urllib.request.Request(
            f"{repo_url}/index-v1.json",
            headers={"User-Agent": "AppVerifierBG-verify/1.0"},
        )
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read())
    except Exception:
        return None


def get_latest_apk_name(index, package):
    entry = index.get("packages", {}).get(package)
    if not entry:
        return None
    versions = sorted(entry, key=lambda v: v.get("versionCode", 0), reverse=True)
    return versions[0].get("apkName") if versions else None


def check_apk(url, expected_pkg=None, timeout=60):
    with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as f:
        apk_path = f.name
    try:
        ok, err = download(url, apk_path, timeout)
        if not ok:
            return None, err
        if os.path.getsize(apk_path) == 0:
            return None, "downloaded file is empty"
        if not _is_valid_apk(apk_path):
            return None, "downloaded file is not a valid APK"
        if expected_pkg:
            if not is_valid_package_name(expected_pkg):
                return None, f"invalid expected package name: {expected_pkg}"
            actual_pkg = extract_package_name(apk_path)
            if actual_pkg and actual_pkg != expected_pkg:
                return None, f"package name mismatch: expected {expected_pkg}, got {actual_pkg}"
        try:
            fp = extract_fingerprint(apk_path)
        except ExtractionError as e:
            return None, str(e)
        if not fp:
            return None, "could not extract certificate fingerprint"
        return fp, None
    finally:
        try:
            os.unlink(apk_path)
        except OSError:
            pass


def _custom_fdroid_names(packages):
    names = set()
    for app in packages:
        for sig in app.get("signature", []):
            for src in sig.get("sources", []):
                name = src.get("name", "")
                if name.startswith("F-Droid (") and name not in FDROID_REPOS:
                    names.add(name)
    return names


def check_fdroid_source(package, repo_url):
    index = fetch_fdroid_index(repo_url)
    if not index:
        return None, "could not fetch F-Droid repo index"
    apk_name = get_latest_apk_name(index, package)
    if not apk_name:
        return None, "package not found in F-Droid repo"
    apk_url = f"{repo_url}/{apk_name}"
    return check_apk(apk_url, expected_pkg=package)


def check_google_play(package, timeout=120):
    if not is_valid_package_name(package):
        return None, f"invalid expected package name: {package}"

    email = os.environ.get("GOOGLE_PLAY_EMAIL", "")
    aas_token = os.environ.get("GOOGLE_PLAY_AAS_TOKEN", "")
    if not email or not aas_token:
        return None, "GOOGLE_PLAY_EMAIL/AAS_TOKEN not set"

    try:
        from googleplay import GooglePlayClient, GooglePlayError, TermsOfServiceError
    except ImportError:
        return None, "googleplay-python not installed (pip install googleplay-python)"

    try:
        api = GooglePlayClient(email=email, aas_token=aas_token)
        api.login()
    except TermsOfServiceError as e:
        return None, f"Google Play ToS error: {e}"
    except GooglePlayError as e:
        return None, f"Google Play login failed: {e}"

    workdir = tempfile.mkdtemp()
    try:
        delivery = api.download(package)
        apk_path = os.path.join(workdir, f"{package}.apk")
        with open(apk_path, "wb") as f:
            for chunk in delivery["file"].iter_content(api.session):
                if chunk:
                    f.write(chunk)

        if not os.path.getsize(apk_path):
            return None, "downloaded APK is empty"

        actual_pkg = extract_package_name(apk_path)
        if actual_pkg and actual_pkg != package:
            return None, f"package name mismatch: expected {package}, got {actual_pkg}"

        try:
            fp = extract_fingerprint(apk_path)
        except ExtractionError as e:
            return None, str(e)
        if not fp:
            return None, "could not extract certificate fingerprint"
        return fp, None
    except GooglePlayError as e:
        msg = str(e).lower()
        if ("400" in msg and "purchase" in msg) or "not purchased" in msg or "no download url" in msg:
            return None, "PAID APP — requires purchase or not available for download"
        return None, f"Google Play download error: {e}"
    except Exception as e:
        msg = str(e).lower()
        if ("400" in msg and "purchase" in msg) or "not purchased" in msg or "no download url" in msg:
            return None, "PAID APP — requires purchase or not available for download"
        return None, f"Google Play download error: {e}"
    finally:
        try:
            shutil.rmtree(workdir)
        except OSError:
            pass


def verify_package(app, source_filter, results, stats):
    pkg = app.get("package", "")

    # Collect all fingerprints across all signature blocks for this package.
    # Some packages (e.g. com.facebook.katana) have multiple signature entries
    # in data.yml — one with both old and new keys after a Google Play App
    # Signing rotation, and another with only the old key.  We need to match
    # against all of them to avoid false mismatches.
    all_recorded_fps = set()
    for sig in app.get("signature", []):
        raw_fp = sig.get("fingerprint", "")
        for chunk in split_fingerprints(raw_fp):
            all_recorded_fps.add(normalize_fp(chunk))

    for sig in app.get("signature", []):
        raw_fp = sig.get("fingerprint", "")
        recorded = normalize_fp(raw_fp)
        if not recorded:
            continue
        recorded_chunks = split_fingerprints(raw_fp)
        is_multi = len(recorded_chunks) > 1
        for src in sig.get("sources", []):
            name = src.get("name", "")
            if source_filter and name not in source_filter:
                continue
            result = {
                "package": pkg,
                "source": name,
                "recorded": recorded,
                "actual": None,
                "status": "error",
                "error": None,
                "multi_signer": is_multi,
            }
            if name == DIRECT_SOURCE:
                link = src.get("apk", {}).get("link", "")
                if not link:
                    result["error"] = "no APK link in source entry"
                else:
                    actual, err = check_apk(link, expected_pkg=pkg)
                    result["actual"] = actual
                    result["error"] = err
            elif name in FDROID_REPOS:
                actual, err = check_fdroid_source(pkg, FDROID_REPOS[name])
                result["actual"] = actual
                result["error"] = err
            elif name.startswith("F-Droid ("):
                repo = (src.get("apk") or {}).get("repo", "")
                if not repo:
                    result["error"] = "no repo URL in custom F-Droid source"
                else:
                    actual, err = check_fdroid_source(pkg, repo)
                    result["actual"] = actual
                    result["error"] = err
            elif name == GOOGLE_PLAY_SOURCE:
                actual, err = check_google_play(pkg)
                result["actual"] = actual
                result["error"] = err
            else:
                result["error"] = f"unsupported source type: {name}"
            if result["error"]:
                result["status"] = "error"
            elif recorded_chunks and actual in recorded_chunks:
                result["status"] = "match"
                result["matched_chunk"] = recorded_chunks.index(actual)
            elif actual in all_recorded_fps:
                result["status"] = "match"
            elif actual == recorded:
                result["status"] = "match"
            else:
                result["status"] = "mismatch"
            stats[result["status"]] += 1
            results.append(result)


def _short_fp(fp):
    if not fp:
        return "N/A"
    return fp[:23] + "..." + fp[-5:] if len(fp) > 29 else fp


def print_summary(results, stats):
    total = sum(stats.values())
    print(f"\n{'='*60}")
    print(f"  Database Verification Report")
    print(f"{'='*60}")
    print(f"  Total:     {total}")
    print(f"  Matches:   {stats['match']}")
    print(f"  Mismatches:{stats['mismatch']}")
    print(f"  Errors:    {stats['error']}")
    print(f"{'='*60}")
    if stats["mismatch"] > 0:
        print("\n  MISMATCHES:")
        for r in results:
            if r["status"] == "mismatch":
                label = " (multi-signer)" if r.get("multi_signer") else ""
                print(f"    {r['package']} ({r['source']}){label}")
                print(f"      recorded: {_short_fp(r['recorded'])}")
                print(f"      actual:   {_short_fp(r['actual'])}")
    if stats["error"] > 0:
        print("\n  ERRORS:")
        for r in results:
            if r["status"] == "error":
                print(f"    {r['package']} ({r['source']}): {r['error']}")
    if stats["match"] > 0:
        print("\n  MATCHES:")
        for r in results:
            if r["status"] == "match":
                label = " (multi-signer)" if r.get("multi_signer") else ""
                print(f"    {r['package']} ({r['source']}){label}")


def main():
    parser = argparse.ArgumentParser(
        description="Verify database entries by downloading APKs"
    )
    parser.add_argument("--data-yml", required=True, help="Path to data.yml")
    parser.add_argument(
        "--mode",
        choices=["direct", "fdroid", "gplay", "all"],
        default="all",
    )
    parser.add_argument("--packages", help="Comma-separated package list")
    parser.add_argument(
        "--percent", type=int, default=0, help="Random sample (0 = all)"
    )
    parser.add_argument("--output", default="", help="Write JSON report to path")
    args = parser.parse_args()

    packages = load_data_yml(args.data_yml)

    if args.packages:
        pkg_set = {p.strip() for p in args.packages.split(",")}
        packages = [p for p in packages if p.get("package") in pkg_set]
    elif args.percent:
        import random

        random.shuffle(packages)
        count = max(1, int(len(packages) * args.percent / 100))
        packages = packages[:count]

    custom_fdroid = _custom_fdroid_names(packages) if args.mode != "direct" else set()
    if args.mode == "direct":
        source_filter = {DIRECT_SOURCE}
    elif args.mode == "fdroid":
        source_filter = FDROID_SOURCES | custom_fdroid
    elif args.mode == "gplay":
        source_filter = {GOOGLE_PLAY_SOURCE}
    else:
        source_filter = {DIRECT_SOURCE} | FDROID_SOURCES | {GOOGLE_PLAY_SOURCE} | custom_fdroid

    stats = {"match": 0, "mismatch": 0, "error": 0}
    results = []

    for app in packages:
        verify_package(app, source_filter, results, stats)

    print_summary(results, stats)

    if args.output:
        with open(args.output, "w") as f:
            json.dump({"stats": stats, "results": results}, f, indent=2)

    sys.exit(1 if stats["mismatch"] > 0 else 0)


if __name__ == "__main__":
    main()
