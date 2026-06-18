#!/usr/bin/env python3
"""Merge HTTPS verified domains into the database without network calls.

Reads verified_domains.json (list of package names) and injects
Source.VERIFIED_DOMAIN_HTTPS into InternalVerificationInfoDatabase.kt
wherever it's missing.  No HTTP calls, no YAML.

Intended to run after generate_internal_db.py so the HTTPS sources survive
a regenerate.
"""

import json
import os
import sys

from backfill_https_domains import add_https_source_to_all_hashes

KOTLIN_FILE = (
    "app/src/main/kotlin/dev/soupslurpr/appverifier/InternalVerificationInfoDatabase.kt"
)
JSON_FILE = "app/verified_domains.json"


def main():
    if not os.path.exists(JSON_FILE):
        print("verified_domains.json not found, nothing to merge", file=sys.stderr)
        return 0

    with open(JSON_FILE, "r", encoding="utf-8-sig") as f:
        packages = json.load(f)

    if not packages:
        print("verified_domains.json is empty, nothing to merge", file=sys.stderr)
        return 0

    with open(KOTLIN_FILE, "r", encoding="utf-8") as f:
        kotlin_text = f.read()

    modified = 0
    already_had = 0
    for pkg in packages:
        new_text = add_https_source_to_all_hashes(kotlin_text, pkg)
        if new_text is kotlin_text:
            already_had += 1
        else:
            kotlin_text = new_text
            modified += 1

    print(f"Merged {modified}, {already_had} already had", file=sys.stderr)

    if modified:
        with open(KOTLIN_FILE, "w", encoding="utf-8", newline="") as f:
            f.write(kotlin_text)

    return 0


if __name__ == "__main__":
    sys.exit(main())
