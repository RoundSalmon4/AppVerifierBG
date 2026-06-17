#!/usr/bin/env python3
"""Check packages in privacyguides/verified-apps data.yml for
self-verified domains via .well-known/assetlinks.json and inject
Source.VERIFIED_DOMAIN_HTTPS into InternalVerificationInfoDatabase.kt.

Operates directly on the Kotlin file, independent of generate_internal_db.py.
"""

import argparse
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict

import yaml

DEFAULT_KOTLIN = (
    "app/src/main/kotlin/dev/soupslurpr/appverifier/InternalVerificationInfoDatabase.kt"
)
USER_AGENT = "AppVerifierBackfill/1.0"
REQUEST_DELAY = 0.3

S20 = "                    "

ssl_ctx = ssl.create_default_context()


def fetch_url(url, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout, context=ssl_ctx) as resp:
        return resp.read().decode("utf-8", errors="replace")


def derive_domain(package_name):
    parts = package_name.split(".")
    if len(parts) < 2:
        return None
    return f"{parts[1]}.{parts[0]}"


def normalize_fingerprint(fp):
    return fp.replace(":", "").upper()


def load_data_yaml(path):
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if isinstance(data, dict):
        data = data.get("packages", [])
    return data


def check_assetlinks(domain):
    url = f"https://{domain}/.well-known/assetlinks.json"
    try:
        body = fetch_url(url)
    except Exception:
        return None

    try:
        statements = json.loads(body)
    except (json.JSONDecodeError, TypeError):
        return None

    if not isinstance(statements, list):
        return None

    result = defaultdict(set)
    for item in statements:
        if not isinstance(item, dict):
            continue
        target = item.get("target", {})
        if not isinstance(target, dict):
            continue
        if target.get("namespace") != "android_app":
            continue
        pkg = target.get("package_name", "")
        fps = target.get("sha256_cert_fingerprints", [])
        if pkg and isinstance(fps, list):
            for fp in fps:
                if isinstance(fp, str):
                    result[pkg].add(normalize_fingerprint(fp))
    return dict(result) if result else None


def extract_balanced(text, start):
    depth = 1
    i = start
    while i < len(text):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        elif c in "'\"`":
            delim = c
            i += 1
            while i < len(text):
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == delim:
                    break
                i += 1
        i += 1
    return len(text)


def find_entry_by_package(text, package):
    """Return (entry_start, entry_end) for the given package, or None."""
    pattern = re.compile(
        r'InternalDatabaseVerificationInfo\(\n\s*"' + re.escape(package) + r'"'
    )
    m = pattern.search(text)
    if not m:
        return None

    entry_start = m.start()
    paren_pos = entry_start + len("InternalDatabaseVerificationInfo(")
    entry_end = extract_balanced(text, paren_pos)
    return entry_start, entry_end


def find_fingerprint_in_entry(text, fingerprint):
    """Return the absolute position of the quoted fingerprint in text, or -1."""
    fp_quoted = f'"{fingerprint}"'
    return text.find(fp_quoted)


def hashes_start_before(text, fp_pos):
    """Find the start of the Hashes block containing the fingerprint position."""
    # Search backward for Hashes(
    hs = text.rfind("Hashes(", 0, fp_pos)
    if hs == -1:
        return None
    return hs


def source_list_bounds_in_hashes(text, hashes_pos):
    """Find the (start, end) of the source listOf inside a Hashes block."""
    # First listOf after Hashes( is the source list
    sl = text.find("listOf(", hashes_pos)
    if sl == -1:
        return None
    content_start = sl + len("listOf(")
    close = extract_balanced(text, content_start)
    return sl, close


def add_https_source_to_kotlin(kotlin_text, package, fingerprint):
    """Add Source.VERIFIED_DOMAIN_HTTPS to the Hashes block for (package, fingerprint).

    Returns the modified text, or original text if nothing changed.
    """
    entry_bounds = find_entry_by_package(kotlin_text, package)
    if not entry_bounds:
        print(f"  skip {package}: entry not found", file=sys.stderr)
        return kotlin_text

    entry_start, entry_end = entry_bounds

    fp_pos = find_fingerprint_in_entry(kotlin_text, fingerprint)
    if fp_pos == -1 or fp_pos >= entry_end:
        print(f"  skip {package}: fingerprint {fingerprint[:16]} not in entry", file=sys.stderr)
        return kotlin_text

    hs = hashes_start_before(kotlin_text, fp_pos)
    if hs is None or hs < entry_start:
        print(f"  skip {package}: no Hashes block found", file=sys.stderr)
        return kotlin_text

    sl_bounds = source_list_bounds_in_hashes(kotlin_text, hs)
    if sl_bounds is None:
        print(f"  skip {package}: no source list in Hashes block", file=sys.stderr)
        return kotlin_text

    sl_open, sl_close = sl_bounds

    # Check if already present
    if "Source.VERIFIED_DOMAIN_HTTPS" in kotlin_text[sl_open:sl_close]:
        return kotlin_text

    # Insert before the closing paren of the source list
    close_paren = sl_close - 1

    # Find the last newline before the closing paren
    last_nl = kotlin_text.rfind("\n", sl_open, close_paren)
    if last_nl == -1:
        # Single-line listOf(Source.X) — unlikely in generated file
        insert = f", Source.VERIFIED_DOMAIN_HTTPS"
        return kotlin_text[:close_paren] + insert + kotlin_text[close_paren:]

    # Add new source line after the last newline
    new_source_line = f"{S20}Source.VERIFIED_DOMAIN_HTTPS,\n"
    insert_pos = last_nl + 1
    return kotlin_text[:insert_pos] + new_source_line + kotlin_text[insert_pos:]


def main():
    parser = argparse.ArgumentParser(
        description="Backfill HTTPS Verified Domain sources from assetlinks.json"
    )
    parser.add_argument(
        "--data-yml",
        metavar="PATH",
        required=True,
        help="Path to privacyguides/verified-apps data.yml",
    )
    parser.add_argument(
        "--kotlin",
        metavar="PATH",
        default=DEFAULT_KOTLIN,
        help=f"Path to InternalVerificationInfoDatabase.kt (default: {DEFAULT_KOTLIN})",
    )
    args = parser.parse_args()

    entries = load_data_yaml(args.data_yml)

    # Build domain -> [(package, [fingerprints])] map
    pkgs = []
    for app in entries:
        pkg = app.get("package", "")
        if not pkg:
            continue
        sigs = app.get("signature", [])
        fingerprints = []
        for sig in sigs:
            fp = sig.get("fingerprint", "").strip()
            if fp:
                fingerprints.append(fp)
        if fingerprints:
            pkgs.append((pkg, fingerprints))

    domain_to_pkgs = defaultdict(list)
    for pkg, fps in pkgs:
        d = derive_domain(pkg)
        if d:
            domain_to_pkgs[d].append((pkg, fps))

    total_domains = len(domain_to_pkgs)
    print(f"Packages with fingerprints: {len(pkgs)}", file=sys.stderr)
    print(f"Derived unique domains:     {total_domains}", file=sys.stderr)

    # Check assetlinks for each domain and collect matches
    matches = []

    for i, (domain, pkg_list) in enumerate(
        sorted(domain_to_pkgs.items(), key=lambda x: x[0].lower()), 1
    ):
        if i % 20 == 0:
            print(f"  Progress: {i}/{total_domains}...", file=sys.stderr)

        assetlinks = check_assetlinks(domain)
        if assetlinks is None:
            time.sleep(REQUEST_DELAY)
            continue

        for pkg, known_fps in pkg_list:
            pkg_assetlinks_fps = assetlinks.get(pkg)
            if pkg_assetlinks_fps is None:
                continue
            for known_fp in known_fps:
                nfp = normalize_fingerprint(known_fp)
                if nfp in pkg_assetlinks_fps:
                    matches.append((pkg, known_fp))
                    break

        time.sleep(REQUEST_DELAY)

    deduplicated = list(dict.fromkeys(matches))
    print(f"\nMatches found (after dedup): {len(deduplicated)}", file=sys.stderr)

    if not deduplicated:
        print("Nothing to backfill.", file=sys.stderr)
        return

    # Read Kotlin file
    with open(args.kotlin, "r", encoding="utf-8") as f:
        kotlin_text = f.read()

    modified = 0
    for pkg, fp in sorted(deduplicated):
        new_text = add_https_source_to_kotlin(kotlin_text, pkg, fp)
        if new_text is not kotlin_text:
            kotlin_text = new_text
            modified += 1
            print(f"  modified: {pkg} ({fp[:16]}...)", file=sys.stderr)
        else:
            print(f"  skipped:  {pkg} ({fp[:16]}...)", file=sys.stderr)

    print(f"\nModified {modified} entries", file=sys.stderr)

    if modified:
        with open(args.kotlin, "w", encoding="utf-8", newline="") as f:
            f.write(kotlin_text)
        print(f"Written to {args.kotlin}", file=sys.stderr)


if __name__ == "__main__":
    main()
