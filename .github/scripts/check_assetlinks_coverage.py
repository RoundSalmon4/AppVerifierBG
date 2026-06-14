#!/usr/bin/env python3
"""Experiment: check how many apps in privacyguides/verified-apps
have self-verified domains via .well-known/assetlinks.json.
"""

import sys
import json
import ssl
import time
import urllib.request
import urllib.error
from urllib.parse import urlparse
from collections import defaultdict

try:
    import yaml
except ImportError:
    print("Missing pyyaml. Install with: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

DATA_URL = "https://raw.githubusercontent.com/privacyguides/verified-apps/main/data.yml"

KNOWN_PLATFORMS = {
    "github.com", "codeberg.org", "gitlab.com", "gitlab.freedesktop.org",
    "bitbucket.org", "sourceforge.net", "gitgud.io", "framagit.org",
    "notabug.org", "gitea.com", "sr.ht", "git.sr.ht",
    "play.google.com", "apps.apple.com", "f-droid.org",
}

ssl_ctx = ssl.create_default_context()


def fetch_url(url, timeout=15):
    req = urllib.request.Request(
        url, headers={"User-Agent": "AppVerifierAssetlinksExperiment/1.0"}
    )
    with urllib.request.urlopen(req, timeout=timeout, context=ssl_ctx) as resp:
        return resp.read().decode("utf-8", errors="replace")


def extract_domains_from_data(data):
    entries = data if isinstance(data, list) else data.get("packages", [])
    domain_map = defaultdict(list)

    for app in entries:
        pkg = app.get("package", "")
        if not pkg:
            continue

        all_fps = []
        for sig in app.get("signature", []):
            fp = sig.get("fingerprint", "").strip()
            if fp:
                for line in fp.splitlines():
                    line = line.strip()
                    if line:
                        all_fps.append(line)

            sources = sig.get("sources", [])
            for src in sources:
                link = src.get("link", "")
                if link:
                    parsed = urlparse(link)
                    domain = parsed.hostname
                    if domain:
                        domain_map[domain].append((pkg, all_fps[:]))

    return domain_map


def check_assetlinks(domain, expected_packages):
    url = f"https://{domain}/.well-known/assetlinks.json"
    try:
        body = fetch_url(url)
    except Exception as e:
        return None, str(e)

    try:
        data = json.loads(body)
    except json.JSONDecodeError as e:
        return None, f"invalid JSON: {e}"

    if not isinstance(data, list):
        return None, "not a JSON array"

    results = []
    for entry in data:
        if not isinstance(entry, dict):
            continue
        target = entry.get("target", {})
        if not isinstance(target, dict):
            continue
        ns = target.get("namespace", "")
        if ns != "android_app":
            continue
        pkg_name = target.get("package_name", "")
        fps = target.get("sha256_cert_fingerprints", [])
        if not pkg_name or not fps:
            continue
        if not isinstance(fps, list):
            fps = [fps]
        results.append((pkg_name, fps))

    if not results:
        return None, "no android_app entries found"

    return results, None


def main():
    print("Fetching privacyguides/verified-apps data.yml...", file=sys.stderr)
    try:
        raw = fetch_url(DATA_URL)
    except Exception as e:
        print(f"Failed to fetch data.yml: {e}", file=sys.stderr)
        sys.exit(1)

    data = yaml.safe_load(raw)
    entries = data if isinstance(data, list) else data.get("packages", [])
    total_apps = len(entries)
    print(f"Found {total_apps} apps in database", file=sys.stderr)

    domain_map = extract_domains_from_data(data)
    total_domains = len(domain_map)
    platform_domains = {d for d in domain_map if d in KNOWN_PLATFORMS}
    custom_domains = {d for d in domain_map if d not in KNOWN_PLATFORMS}

    print(f"Total unique domains extracted from links: {total_domains}", file=sys.stderr)
    print(f"  - Hosting platforms (skipped): {len(platform_domains)}", file=sys.stderr)
    print(f"  - Custom domains (to check):   {len(custom_domains)}", file=sys.stderr)
    print(file=sys.stderr)

    checked = 0
    served_assetlinks = 0
    matched_fingerprint = 0
    mismatched_fingerprint = 0
    different_package = 0

    match_details = []
    mismatch_details = []
    diff_pkg_details = []
    no_assetlinks = []

    for domain in sorted(custom_domains, key=lambda d: d.lower()):
        expected_list = domain_map[domain]
        unique_pkgs = set(p[0] for p in expected_list)
        expected_fps = set()
        for pkg, fps in expected_list:
            for fp in fps:
                expected_fps.add(fp)

        checked += 1
        if checked % 10 == 0:
            print(f"  Progress: {checked}/{len(custom_domains)}...", file=sys.stderr)

        results, error = check_assetlinks(domain, unique_pkgs)
        if error or not results:
            no_assetlinks.append((domain, error or "no results", unique_pkgs))
            continue

        served_assetlinks += 1

        for pkg_name, fps in results:
            fp_set = set(f.replace(" ", "").upper() for f in fps)
            expected_clean = set()
            for fp in expected_fps:
                clean = fp.replace(" ", "").upper()
                if clean:
                    expected_clean.add(clean)

            if pkg_name in unique_pkgs:
                if expected_clean and fp_set == expected_clean:
                    matched_fingerprint += 1
                    match_details.append((domain, pkg_name, fps))
                elif expected_clean:
                    mismatched_fingerprint += 1
                    mismatch_details.append((domain, pkg_name, fps, list(expected_clean)))
            else:
                different_package += 1
                diff_pkg_details.append((domain, pkg_name, unique_pkgs))

        time.sleep(0.3)

    print(file=sys.stderr)
    print("=" * 60, file=sys.stderr)
    print("COVERAGE REPORT", file=sys.stderr)
    print("=" * 60, file=sys.stderr)
    print(f"Total apps in database:              {total_apps}", file=sys.stderr)
    print(f"Total unique domains extracted:      {total_domains}", file=sys.stderr)
    print(f"Custom domains checked:              {len(custom_domains)}", file=sys.stderr)
    print(f"Domains serving valid assetlinks:    {served_assetlinks}", file=sys.stderr)
    print(f"  - Fingerprint MATCHES DB:          {matched_fingerprint}", file=sys.stderr)
    print(f"  - Fingerprint MISMATCHES DB:       {mismatched_fingerprint}", file=sys.stderr)
    print(f"  - Assetlinks for DIFFERENT pkg:    {different_package}", file=sys.stderr)
    print(f"Domains with NO valid assetlinks:    {len(no_assetlinks)}", file=sys.stderr)

    if match_details:
        print(file=sys.stderr)
        print("MATCHED domains:", file=sys.stderr)
        for d, pkg, fps in match_details:
            print(f"  {d} -> {pkg}", file=sys.stderr)

    if mismatch_details:
        print(file=sys.stderr)
        print("MISMATCHED fingerprints:", file=sys.stderr)
        for d, pkg, asset_fps, db_fps in mismatch_details:
            print(f"  {d} -> {pkg}: assetlinks={asset_fps[0][:20]}... DB={db_fps[0][:20] if db_fps else 'none'}...", file=sys.stderr)

    if diff_pkg_details:
        print(file=sys.stderr)
        print("ASSETLINKS FOR DIFFERENT PACKAGE:", file=sys.stderr)
        for d, pkg, expected_set in diff_pkg_details:
            print(f"  {d} -> {pkg} (expected one of: {', '.join(expected_set)})", file=sys.stderr)

    report = {
        "totalApps": total_apps,
        "totalDomains": total_domains,
        "customDomainsChecked": len(custom_domains),
        "domainsServingAssetlinks": served_assetlinks,
        "matches": matched_fingerprint,
        "mismatches": mismatched_fingerprint,
        "differentPackage": different_package,
        "noAssetlinks": len(no_assetlinks),
        "matchedEntries": [(d, p, f[0] if f else "") for d, p, f in match_details],
        "mismatchedEntries": [(d, p, f[0] if f else "") for d, p, f, _ in mismatch_details],
    }
    output_path = ".github/scripts/coverage_report.json"
    with open(output_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"\nReport saved to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
