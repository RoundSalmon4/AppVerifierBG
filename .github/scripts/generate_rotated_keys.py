#!/usr/bin/env python3
"""Generate user-importable rotated-keys files from the committed store.

Reads rotated-keys/rotated_keys.json (the durable, human-curated source) and
produces rotated-keys.txt / .yaml / .json in the same formats the user database
import accepts, so users can add newly-rotated signing keys to their user
database while upstream database entries are updated.

These keys are NOT independently verified by AppVerifier BG. They come from
spot-check mismatches where an installed app's certificate no longer matches
the recorded (old) key, and the maintainer confirmed the new key looks like a
legitimate rotation rather than a tampered/typosquatted build. Users who import
them do so at their own risk, consistent with community-submitted hashes.
"""

import argparse
import json
import os
import sys

STORE_PATH = os.path.join(
    os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ),
    "rotated-keys",
    "rotated_keys.json",
)


def split_fingerprint(fp):
    """Return a valid fingerprint as a colon-separated SHA-256, or None."""
    if not fp:
        return None
    cleaned = fp.replace(":", "").strip().upper()
    if len(cleaned) != 64 or not all(c in "0123456789ABCDEF" for c in cleaned):
        return None
    return ":".join(cleaned[i : i + 2] for i in range(0, 64, 2))


def load_store(path):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    entries = data.get("entries", [])
    result = []
    for e in entries:
        pkg = e.get("package", "").strip()
        new_key = split_fingerprint(e.get("new_key"))
        old_keys = [split_fingerprint(k) for k in e.get("old_keys", [])]
        old_keys = [k for k in old_keys if k]
        if not pkg or not new_key:
            print(f"SKIP_INVALID entry: package={pkg!r} new_key={e.get('new_key')!r}")
            continue
        result.append(
            {
                "package": pkg,
                "hashes": [new_key],
                "hasMultipleSigners": False,
                "old_keys": old_keys,
                "source_issue": e.get("source_issue"),
            }
        )
    result.sort(key=lambda r: r["package"])
    return result


def write_txt(entries, path):
    with open(path, "w", encoding="utf-8") as f:
        blocks = []
        for e in entries:
            blocks.append(e["package"] + "\n" + "\n".join(e["hashes"]))
        f.write("\n\n".join(blocks) + "\n")


def write_yaml(entries, path):
    with open(path, "w", encoding="utf-8") as f:
        for e in entries:
            f.write("---\n")
            f.write(f"packageName: {e['package']}\n")
            f.write("hashes:\n")
            for h in e["hashes"]:
                f.write(f"  - {h}\n")


def write_json(entries, path):
    out = [
        {
            "packageName": e["package"],
            "hashes": e["hashes"],
            "hasMultipleSigners": e["hasMultipleSigners"],
        }
        for e in entries
    ]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)
        f.write("\n")


def main():
    parser = argparse.ArgumentParser(description="Generate rotated-keys files from the store.")
    parser.add_argument("--store", default=STORE_PATH, help="Path to rotated_keys.json")
    parser.add_argument(
        "--txt-only",
        action="store_true",
        help="Only write the .txt output (used by the nightly build).",
    )
    parser.add_argument(
        "output",
        help="Path for the JSON output; .txt and .yaml are derived from it.",
    )
    args = parser.parse_args()

    if not os.path.exists(args.store):
        print("STORE_MISSING", args.store)
        sys.exit(1)

    entries = load_store(args.store)
    print(f"LOADED {len(entries)} rotated-key entries")

    base, _ = os.path.splitext(args.output)
    txt_path = base + ".txt"
    json_path = base + ".json"
    yaml_path = base + ".yaml"

    write_txt(entries, txt_path)
    write_json(entries, json_path)
    if not args.txt_only:
        write_yaml(entries, yaml_path)
        print(f"WROTE {txt_path}, {json_path}, {yaml_path}")
    else:
        print(f"WROTE {txt_path}, {json_path}")

    if not entries:
        print("NO_CHANGES")


if __name__ == "__main__":
    main()
