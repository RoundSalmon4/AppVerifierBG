#!/usr/bin/env python3
"""Scrape GrapheneOS forum thread for community-submitted app hashes."""

import re
import json
import sys
import urllib.request
import urllib.error

FORUM_URL = "https://discuss.grapheneos.org/d/15368-lets-compare-hashes-for-apps-not-in-appverifiers-database"

PACKAGE_RE = re.compile(r'^[a-zA-Z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$')
HASH_RE = re.compile(r'^[A-Fa-f0-9]{2}(:[A-Fa-f0-9]{2}){31}$')

# Known non-package lines to skip
SKIP_WORDS = {
    "source:", "location:", "edit:", "note:", "app", "i", "you", "it", "is", "are", "was",
    "the", "this", "that", "these", "those", "for", "with", "from", "has",
}


def fetch_page(url):
    req = urllib.request.Request(url, headers={"User-Agent": "AppVerifierCommunityDB/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace")


def extract_noscript_content(html_text):
    start_marker = '<noscript id="flarum-content">'
    end_marker = "</noscript>"
    start = html_text.find(start_marker)
    if start == -1:
        return ""
    start = html_text.index(">", start) + 1
    end = html_text.index(end_marker, start)
    return html_text[start:end]


def extract_posts(html_text):
    raw = extract_noscript_content(html_text)
    posts = re.findall(r'<div class="Post-body">(.*?)</div>\s*</article>', raw, re.DOTALL)
    results = []
    for body_html in posts:
        text = re.sub(r'<[^>]+>', "", body_html)
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        text = text.replace("&quot;", '"').replace("&#039;", "'").replace("&#39;", "'")
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        entries = parse_entries(lines)
        results.extend(entries)
    return results


def is_likely_package_name(word):
    if not PACKAGE_RE.match(word):
        return False
    first = word.split('.')[0].lower()
    if first in SKIP_WORDS:
        return False
    return True


def is_hash(word):
    return bool(HASH_RE.match(word))


def parse_entries(lines):
    entries = []
    current_pkg = None
    current_hashes = []
    for line in lines:
        if is_hash(line):
            if current_pkg is not None:
                current_hashes.append(line.upper())
        elif is_likely_package_name(line):
            if current_pkg is not None and current_hashes:
                entries.append({
                    "packageName": current_pkg,
                    "hashes": current_hashes,
                    "hasMultipleSigners": len(current_hashes) > 1,
                })
            current_pkg = line
            current_hashes = []
        else:
            if current_pkg is not None and current_hashes:
                entries.append({
                    "packageName": current_pkg,
                    "hashes": current_hashes,
                    "hasMultipleSigners": len(current_hashes) > 1,
                })
            current_pkg = None
            current_hashes = []
    if current_pkg is not None and current_hashes:
        entries.append({
            "packageName": current_pkg,
            "hashes": current_hashes,
            "hasMultipleSigners": len(current_hashes) > 1,
        })
    return entries


def merge_entries(entries):
    merged = {}
    for e in entries:
        pkg = e["packageName"]
        if pkg in merged:
            all_hashes = list(set(merged[pkg]["hashes"] + e["hashes"]))
            merged[pkg] = {
                "packageName": pkg,
                "hashes": all_hashes,
                "hasMultipleSigners": len(all_hashes) > 1,
            }
        else:
            merged[pkg] = dict(e)
    return sorted(merged.values(), key=lambda x: x["packageName"])


def load_previous(path):
    with open(path) as f:
        data = json.load(f)
    return {e["packageName"]: set(e["hashes"]) for e in data}


def diff_stats(new_entries, previous_path):
    prev = load_previous(previous_path)
    new_map = {e["packageName"]: set(e["hashes"]) for e in new_entries}

    added_pkgs = [p for p in new_map if p not in prev]
    removed_pkgs = [p for p in prev if p not in new_map]
    changed_pkgs = [
        p for p in new_map if p in prev and new_map[p] != prev[p]
    ]

    new_hash_count = sum(len(new_map[p]) - len(prev.get(p, set())) for p in new_map)
    removed_hash_count = sum(len(prev[p]) - len(new_map.get(p, set())) for p in prev)

    print(f"  Added packages: {len(added_pkgs)}", file=sys.stderr)
    for p in added_pkgs:
        print(f"    + {p}", file=sys.stderr)
    print(f"  Removed packages: {len(removed_pkgs)}", file=sys.stderr)
    for p in removed_pkgs:
        print(f"    - {p}", file=sys.stderr)
    print(f"  Changed packages: {len(changed_pkgs)}", file=sys.stderr)
    for p in changed_pkgs:
        print(f"    ~ {p}", file=sys.stderr)
    print(f"  New hashes added to existing packages: {new_hash_count}", file=sys.stderr)
    print(f"  Hashes removed from existing packages: {removed_hash_count}", file=sys.stderr)

    return len(added_pkgs) + len(removed_pkgs) + len(changed_pkgs) > 0


def main():
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--diff", help="Path to previous JSON to diff against")
    parser.add_argument("output", help="Path to write output JSON")
    args = parser.parse_args()

    all_entries = []
    page = 1
    while True:
        url = f"{FORUM_URL}?page={page}" if page > 1 else FORUM_URL
        print(f"Fetching page {page}...", file=sys.stderr)
        try:
            html = fetch_page(url)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                break
            raise
        entries = extract_posts(html)
        if not entries:
            break
        all_entries.extend(entries)
        page += 1

    merged = merge_entries(all_entries)
    print(f"Found {len(merged)} unique packages across {page - 1} page(s)", file=sys.stderr)

    with open(args.output, "w") as f:
        json.dump(merged, f, indent=2)

    if args.diff:
        changed = diff_stats(merged, args.diff)
        if not changed:
            print("NO_CHANGES", file=sys.stderr)


if __name__ == "__main__":
    main()
