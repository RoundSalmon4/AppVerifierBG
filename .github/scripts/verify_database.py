#!/usr/bin/env python3
"""Verify privacyguides database entries by downloading APKs and checking fingerprints."""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

import yaml

DIRECT_SOURCE = "Direct APK Link"
FDROID_SOURCES = {"F-Droid", "F-Droid (IzzyOnDroid)"}
FDROID_OFFICIAL = "https://f-droid.org/repo"
IZZY_URL = "https://apt.izzysoft.de/fdroid/repo"
FDROID_REPOS = {
    "F-Droid": FDROID_OFFICIAL,
    "F-Droid (IzzyOnDroid)": IZZY_URL,
}

FP_RE = re.compile(r"[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}")


def load_data_yml(path):
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if isinstance(data, dict):
        return data.get("packages", [])
    return data


def normalize_fp(fp):
    fp = fp.strip().upper().replace(" ", "").replace(":", "")
    return ":".join(fp[i : i + 2] for i in range(0, len(fp), 2))


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


def extract_fingerprint(apk_path):
    if not os.path.getsize(apk_path):
        return None

    apksigner_path = _find_apksigner() or "apksigner"
    try:
        result = subprocess.run(
            [apksigner_path, "verify", "--print-certs", apk_path],
            capture_output=True,
            text=True,
            timeout=15,
        )
        for line in result.stdout.splitlines():
            m = FP_RE.search(line)
            if m:
                return normalize_fp(m.group(0))
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    # keytool -printcert -jarfile works on JAR-signed APKs (no SDK needed)
    try:
        result = subprocess.run(
            ["keytool", "-printcert", "-jarfile", apk_path],
            capture_output=True,
            text=True,
            timeout=15,
        )
        m = FP_RE.search(result.stdout)
        if m:
            return normalize_fp(m.group(0))
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

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
                m = FP_RE.search(cert_result.stdout.decode())
                if m:
                    return normalize_fp(m.group(0))
        except (subprocess.TimeoutExpired, FileNotFoundError):
            pass

    return None


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


def check_apk(url, timeout=60):
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
        fp = extract_fingerprint(apk_path)
        if not fp:
            return None, "could not extract certificate fingerprint"
        return fp, None
    finally:
        try:
            os.unlink(apk_path)
        except OSError:
            pass


def check_fdroid_source(package, repo_url):
    index = fetch_fdroid_index(repo_url)
    if not index:
        return None, "could not fetch F-Droid repo index"
    apk_name = get_latest_apk_name(index, package)
    if not apk_name:
        return None, "package not found in F-Droid repo"
    apk_url = f"{repo_url}/{apk_name}"
    return check_apk(apk_url)


def verify_package(app, source_filter, results, stats):
    pkg = app.get("package", "")
    for sig in app.get("signature", []):
        recorded = normalize_fp(sig.get("fingerprint", ""))
        if not recorded:
            continue
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
            }
            if name == DIRECT_SOURCE:
                link = src.get("apk", {}).get("link", "")
                if not link:
                    result["error"] = "no APK link in source entry"
                else:
                    actual, err = check_apk(link)
                    result["actual"] = actual
                    result["error"] = err
            elif name in FDROID_REPOS:
                actual, err = check_fdroid_source(pkg, FDROID_REPOS[name])
                result["actual"] = actual
                result["error"] = err
            else:
                result["error"] = f"unsupported source type: {name}"
            if result["error"]:
                result["status"] = "error"
            elif actual == recorded:
                result["status"] = "match"
            else:
                result["status"] = "mismatch"
            stats[result["status"]] += 1
            results.append(result)


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
                print(f"    {r['package']} ({r['source']})")
                print(f"      recorded: {r['recorded']}")
                print(f"      actual:   {r['actual']}")
    if stats["error"] > 0:
        print("\n  ERRORS:")
        for r in results:
            if r["status"] == "error":
                print(f"    {r['package']} ({r['source']}): {r['error']}")


def main():
    parser = argparse.ArgumentParser(
        description="Verify database entries by downloading APKs"
    )
    parser.add_argument("--data-yml", required=True, help="Path to data.yml")
    parser.add_argument(
        "--mode",
        choices=["direct", "fdroid", "all"],
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

    if args.mode == "direct":
        source_filter = {DIRECT_SOURCE}
    elif args.mode == "fdroid":
        source_filter = FDROID_SOURCES
    else:
        source_filter = {DIRECT_SOURCE} | FDROID_SOURCES

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
