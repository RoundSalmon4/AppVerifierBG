#!/usr/bin/env python3
"""Check packages in privacyguides/verified-apps data.yml for
self-verified domains via .well-known/assetlinks.json and inject
Source.VERIFIED_DOMAIN_HTTPS into InternalVerificationInfoDatabase.kt.

Only adds domain verification when the signing certificate fingerprint
declared in assetlinks.json matches a fingerprint already known for that
package in data.yml.  This prevents a fake app that copies a legitimate
package name from inheriting domain verification meant for the real app.

Operates directly on the Kotlin file, independent of generate_internal_db.py.
"""

import argparse
import json
import os
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
MAX_ASSETLINKS_SIZE = 1048576  # 1 MB, matching upstream --max-filesize

S20 = "                    "

ssl_ctx = ssl.create_default_context()


def normalize_fingerprint(fp):
    """Normalize a fingerprint to uppercase colon-separated format."""
    return fp.strip().upper().replace(" ", "").replace("\n", "")


def fetch_assetlinks(url, max_size=MAX_ASSETLINKS_SIZE, timeout=20, retries=2):
    """Fetch assetlinks.json following redirects.
    Body capped at 1 MB, retries on transient errors (2 retries, 1s delay).
    Returns body string or None.
    """
    for attempt in range(1 + retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout, context=ssl_ctx) as resp:
                body = resp.read(max_size + 1)
                if len(body) > max_size:
                    return None
                return body.decode("utf-8", errors="replace")
        except Exception:
            if attempt < retries:
                time.sleep(1)
            else:
                return None
    return None


def derive_domain(package_name):
    parts = package_name.split(".")
    if len(parts) < 2:
        return None
    return f"{parts[1]}.{parts[0]}"


def check_assetlinks_packages_and_fingerprints(domain, max_docs=10):
    """Fetch assetlinks.json and return a dict mapping package names to the
    set of sha256_cert_fingerprints declared for them.

    Follows `include` references (bounded by max_docs) per upstream's
    domain_fetch_https_record. Returns None if the top-level file is
    missing or unparseable.
    """
    url = f"https://{domain}/.well-known/assetlinks.json"
    visited = set()
    queue = [url]
    docs = 0
    found_top = False
    pkg_to_fps = defaultdict(set)

    while queue and docs < max_docs:
        cur = queue.pop(0)
        if cur in visited:
            continue
        visited.add(cur)

        body = fetch_assetlinks(cur)
        if body is None:
            if cur == url:
                return None
            continue

        docs += 1
        if cur == url:
            found_top = True

        try:
            statements = json.loads(body)
        except json.JSONDecodeError:
            continue

        if isinstance(statements, dict):
            statements = [statements]
        elif not isinstance(statements, list):
            continue

        for item in statements:
            if not isinstance(item, dict):
                continue
            # Follow include references
            if "include" in item and isinstance(item["include"], str):
                inc_url = item["include"]
                if inc_url.startswith("https://") and inc_url not in visited:
                    queue.append(inc_url)
            # Collect android_app statements with fingerprints
            target = item.get("target")
            if isinstance(target, dict) and target.get("namespace") == "android_app":
                pkg = target.get("package_name", "")
                if pkg:
                    fps = target.get("sha256_cert_fingerprints", [])
                    for fp in fps:
                        pkg_to_fps[pkg].add(normalize_fingerprint(fp))

    if found_top and pkg_to_fps:
        return dict(pkg_to_fps)
    return None


def load_data_yaml(path):
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if isinstance(data, dict):
        data = data.get("packages", [])
    return data


def build_data_yml_fingerprint_map(entries):
    """Build a dict mapping package names to sets of known fingerprints
    from the data.yml entries."""
    pkg_fps = {}
    for app in entries:
        pkg = app.get("package", "")
        if not pkg:
            continue
        fps = set()
        for sig in app.get("signature", []):
            fp_raw = sig.get("fingerprint", "")
            for line in fp_raw.strip().splitlines():
                normalized = normalize_fingerprint(line)
                if normalized:
                    fps.add(normalized)
        pkg_fps[pkg] = fps
    return pkg_fps


# --- Kotlin text manipulation helpers (unchanged from original) ---


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


def find_all_hashes_in_entry(text, entry_start, entry_end):
    """Return list of (hashes_start, hashes_end) for each Hashes(...) block."""
    result = []
    pos = entry_start
    while pos < entry_end:
        hs = text.find("Hashes(", pos, entry_end)
        if hs == -1:
            break
        paren_start = hs + len("Hashes(")
        paren_end = extract_balanced(text, paren_start)
        if paren_end <= entry_end:
            result.append((hs, paren_end))
        pos = paren_end + 1
    return result


def source_list_bounds_in_hashes(text, hashes_pos):
    """Find the (start, end) of the source listOf inside a Hashes block."""
    sl = text.find("listOf(", hashes_pos)
    if sl == -1:
        return None
    content_start = sl + len("listOf(")
    close = extract_balanced(text, content_start)
    return sl, close


def fingerprints_in_hashes_block(text, hashes_start, hashes_end):
    """Extract fingerprint strings from a Hashes block."""
    block_text = text[hashes_start:hashes_end]
    fps = re.findall(r'"((?:[0-9A-F]{2}:)+[0-9A-F]{2})"', block_text)
    return {normalize_fingerprint(fp) for fp in fps}


def add_https_source_to_matched_hashes(kotlin_text, package, assetlinks_fps):
    """Add Source.VERIFIED_DOMAIN_HTTPS only to Hashes blocks whose
    fingerprints overlap with assetlinks_fps for this package.

    Returns the modified text, or original text if nothing changed.
    """
    entry_bounds = find_entry_by_package(kotlin_text, package)
    if not entry_bounds:
        print(f"  skip {package}: entry not found", file=sys.stderr)
        return kotlin_text

    entry_start, entry_end = entry_bounds
    hashes_blocks = find_all_hashes_in_entry(kotlin_text, entry_start, entry_end)

    modifications = []
    for hs, he in hashes_blocks:
        sl_bounds = source_list_bounds_in_hashes(kotlin_text, hs)
        if sl_bounds is None:
            continue
        sl_open, sl_close = sl_bounds
        if "Source.VERIFIED_DOMAIN_HTTPS" in kotlin_text[sl_open:sl_close]:
            continue

        # Check if any fingerprints in this block match assetlinks
        block_fps = fingerprints_in_hashes_block(kotlin_text, hs, he)
        if not block_fps & assetlinks_fps:
            continue

        # Insert before the closing paren of the source list
        close_paren = sl_close - 1
        last_nl = kotlin_text.rfind("\n", sl_open, close_paren)
        if last_nl == -1:
            insert = ", Source.VERIFIED_DOMAIN_HTTPS"
            modifications.append((close_paren, insert))
        else:
            insert_text = f"{S20}Source.VERIFIED_DOMAIN_HTTPS,\n"
            insert_pos = last_nl + 1
            modifications.append((insert_pos, insert_text))
            # Add comma after the previous last item (before the newline)
            # so Kotlin doesn't see "Source.A\nSource.B" without a separator
            if kotlin_text[last_nl - 1] != ',':
                modifications.append((last_nl, ","))

    if not modifications:
        print(f"  skip {package}: no matching fingerprints in assetlinks.json", file=sys.stderr)
        return kotlin_text

    # Apply right-to-left so positions stay valid
    modifications.sort(key=lambda x: -x[0])
    for pos, insert_text in modifications:
        kotlin_text = kotlin_text[:pos] + insert_text + kotlin_text[pos:]

    print(
        f"  modified: {package} ({len(modifications)} Hashes blocks)",
        file=sys.stderr,
    )
    return kotlin_text


def find_all_packages_with_source(kotlin_text, source_name):
    """Return sorted list of package names whose entry contains source_name."""
    packages = []
    pos = 0
    while True:
        entry_start = kotlin_text.find("InternalDatabaseVerificationInfo(", pos)
        if entry_start == -1:
            break
        paren_start = entry_start + len("InternalDatabaseVerificationInfo(")
        entry_end = extract_balanced(kotlin_text, paren_start)
        entry_text = kotlin_text[entry_start:entry_end]
        m = re.search(r'"([^"]+)"', entry_text)
        if m and source_name in entry_text:
            packages.append(m.group(1))
        pos = entry_end
    return sorted(packages)


def write_verified_domains_json(kotlin_text):
    packages = find_all_packages_with_source(kotlin_text, "Source.VERIFIED_DOMAIN_HTTPS")
    json_path = "app/verified_domains.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(packages, f, indent=2)
        f.write("\n")
    print(f"Exported {len(packages)} packages to {json_path}", file=sys.stderr)


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
    parser.add_argument(
        "--verified-json",
        metavar="PATH",
        default="app/verified_domains.json",
        help="Path to verified_domains.json from previous run (default: app/verified_domains.json)",
    )
    args = parser.parse_args()

    entries = load_data_yaml(args.data_yml)

    # Build fingerprint map from data.yml
    data_yml_fps = build_data_yml_fingerprint_map(entries)

    # Load previously verified packages so we can skip re-checking them
    already_verified = set()
    if os.path.exists(args.verified_json):
        with open(args.verified_json, "r", encoding="utf-8") as f:
            already_verified = set(json.load(f))

    # Re-apply previously verified sources immediately.
    # generate_internal_db.py wipes VERIFIED_DOMAIN_HTTPS for upstream packages
    # on every run, so we restore them now.  This lets the domain-skip logic
    # below safely skip domains without losing the source from the Kotlin file.
    re_modified = 0
    with open(args.kotlin, "r", encoding="utf-8") as f:
        kotlin_text = f.read()
    for pkg in sorted(already_verified):
        # For previously verified packages, re-apply using data.yml fingerprints
        # as a fallback (we don't re-fetch assetlinks.json here)
        pkg_fps = data_yml_fps.get(pkg, set())
        if not pkg_fps:
            # If we can't find fingerprints, skip (shouldn't happen for
            # previously verified packages)
            continue
        new_text = add_https_source_to_matched_hashes(kotlin_text, pkg, pkg_fps)
        if new_text is not kotlin_text:
            kotlin_text = new_text
            re_modified += 1
    if re_modified:
        with open(args.kotlin, "w", encoding="utf-8", newline="") as f:
            f.write(kotlin_text)
    print(f"Re-applied previously verified: {re_modified}", file=sys.stderr)

    # Collect packages and derive domains
    pkgs = []
    for app in entries:
        pkg = app.get("package", "")
        if pkg:
            pkgs.append(pkg)

    domain_to_pkgs = defaultdict(list)
    for pkg in pkgs:
        d = derive_domain(pkg)
        if d:
            domain_to_pkgs[d].append(pkg)

    total_domains = len(domain_to_pkgs)
    print(f"Packages in data.yml:       {len(pkgs)}", file=sys.stderr)
    print(f"Derived unique domains:     {total_domains}", file=sys.stderr)
    print(f"Previously verified pkgs:   {len(already_verified)}", file=sys.stderr)

    # Check assetlinks for each domain and collect fingerprint-matched packages
    matches = {}
    mismatches = []
    serving_domains = 0
    skipped_domains = 0

    for i, (domain, pkg_list) in enumerate(
        sorted(domain_to_pkgs.items(), key=lambda x: x[0].lower()), 1
    ):
        if i % 20 == 0:
            print(f"  Progress: {i}/{total_domains}...", file=sys.stderr)

        # Skip if all packages for this domain are already verified
        if all(pkg in already_verified for pkg in pkg_list):
            skipped_domains += 1
            print(f"  [{i}/{total_domains}] skip {domain}: {len(pkg_list)} pkg(s) already done", file=sys.stderr)
            time.sleep(REQUEST_DELAY)
            continue

        assetlinks_pkg_fps = check_assetlinks_packages_and_fingerprints(domain)
        if assetlinks_pkg_fps is None:
            time.sleep(REQUEST_DELAY)
            continue

        serving_domains += 1

        for pkg in pkg_list:
            if pkg in already_verified:
                continue
            assetlinks_fps = assetlinks_pkg_fps.get(pkg, set())
            if not assetlinks_fps:
                continue
            # Cross-check: only count if at least one fingerprint from
            # assetlinks.json matches a fingerprint in data.yml for this package
            data_fps = data_yml_fps.get(pkg, set())
            if assetlinks_fps & data_fps:
                matches[pkg] = assetlinks_fps
            else:
                mismatches.append((domain, pkg, assetlinks_fps, data_fps))
                print(
                    f"  [{i}/{total_domains}] {domain}: {pkg} fingerprints "
                    f"do not match assetlinks.json ({len(assetlinks_fps)} al fps, "
                    f"{len(data_fps)} data.yml fps)",
                    file=sys.stderr,
                )

        time.sleep(REQUEST_DELAY)

    deduplicated = list(dict.fromkeys(matches.keys()))
    total_matched = len(deduplicated)
    print(f"\nDomains skipped (all done):     {skipped_domains}", file=sys.stderr)
    print(f"Domains serving assetlinks:     {serving_domains}", file=sys.stderr)
    print(f"Matching apps found:           {total_matched}", file=sys.stderr)

    with open(args.kotlin, "r", encoding="utf-8") as f:
        kotlin_text = f.read()

    modified = 0
    for pkg in sorted(deduplicated):
        new_text = add_https_source_to_matched_hashes(kotlin_text, pkg, matches[pkg])
        if new_text is not kotlin_text:
            kotlin_text = new_text
            modified += 1

    print(f"  New sources added:               {modified}", file=sys.stderr)
    print(f"\nModified {modified} entries", file=sys.stderr)

    if mismatches:
        print(f"\nFingerprint Mismatches: {len(mismatches)}", file=sys.stderr)
        for domain, pkg, al_fps, data_fps in mismatches:
            al_display = ", ".join(sorted(al_fps)[:3])
            if len(al_fps) > 3:
                al_display += f" (+{len(al_fps) - 3} more)"
            data_display = ", ".join(sorted(data_fps)[:3])
            if len(data_fps) > 3:
                data_display += f" (+{len(data_fps) - 3} more)"
            print(f"  {domain}: {pkg}", file=sys.stderr)
            print(f"    assetlinks.json:  {al_display}", file=sys.stderr)
            print(f"    data.yml:         {data_display}", file=sys.stderr)

    if modified:
        with open(args.kotlin, "w", encoding="utf-8", newline="") as f:
            f.write(kotlin_text)

    write_verified_domains_json(kotlin_text)


if __name__ == "__main__":
    main()
