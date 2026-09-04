#!/usr/bin/env python3
"""Refresh the rotated-keys store from confirmed spot-check mismatches.

Scans all issues (open and closed) labeled `rotatedkey`. For each, it reads the
maintainer's confirmation comment (authored by round salmon only) which uses a
structured `# rotated-keys` marker followed by `package:` lines listing which
packages in that mismatch issue are confirmed key rotations. It then downloads
that issue's `verify-report` artifact and pulls the authoritative recorded (old)
and actual (new) fingerprints for those packages, merging them into the
committed rotated-keys store.

Only comments authored by the maintainer and matching the exact marker are used,
so free-form context comments are never treated as confirmations.

Requires the `gh` CLI with GITHUB_TOKEN (present in GitHub Actions runners) and
a full clone checkout of the repo.
"""

import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

STORE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "rotated-keys",
    "rotated_keys.json",
)

DATA_YML_URL = (
    "https://raw.githubusercontent.com/privacyguides/verified-apps/main/data.yml"
)

MAINTAINER = "roundsalmon4"
KEY_MARKER = "# rotated-keys"
PACKAGE_RE = re.compile(r"^\s*package:\s*(.+?)\s*$", re.IGNORECASE)
RUN_URL_RE = re.compile(r"Workflow run:\s*[^\s]+/actions/runs/(\d+)")


def split_fingerprint(fp):
    if not fp:
        return None
    cleaned = fp.replace(":", "").strip().upper()
    if len(cleaned) != 64 or not all(c in "0123456789ABCDEF" for c in cleaned):
        return None
    return ":".join(cleaned[i : i + 2] for i in range(0, 64, 2))


def run_gh(args):
    return subprocess.run(["gh"] + args, capture_output=True, text=True, check=True).stdout


def get_labeled_issues():
    issues = []
    for state in ("open", "closed"):
        try:
            out = run_gh(
                [
                    "issue", "list",
                    "--label", "rotatedkey",
                    "--state", state,
                    "--json", "number,body",
                ]
            )
        except subprocess.CalledProcessError:
            continue
        issues.extend(json.loads(out))
    return issues


def get_issue_comments(issue_number):
    out = run_gh(
        [
            "api",
            f"repos/{os.environ['GITHUB_REPOSITORY']}/issues/{issue_number}/comments",
            "--jq", ".[] | {user: .user.login, body: .body}",
        ]
    )
    # --jq ".[] | {...}" emits one JSON object per line; parse them individually.
    comments = []
    for line in out.splitlines():
        line = line.strip()
        if line:
            comments.append(json.loads(line))
    return comments


def extract_confirmed_packages(issue):
    confirmed = set()
    comments = get_issue_comments(issue["number"])
    for c in comments:
        if c.get("user", {}).get("login") != MAINTAINER:
            continue
        body = c.get("body") or ""
        if KEY_MARKER not in body:
            continue
        for line in body.splitlines():
            if line.strip() == KEY_MARKER:
                continue
            m = PACKAGE_RE.match(line)
            if m:
                confirmed.add(m.group(1).strip())
    return confirmed


def get_run_id(issue):
    m = RUN_URL_RE.search(issue.get("body") or "")
    return m.group(1) if m else None


def download_report(run_id, dest_dir):
    """Download the verify-report artifact for a run into dest_dir.

    Returns the path to report.json, or None if the artifact is unavailable
    (e.g. expired or the run did not produce one).
    """
    zip_path = os.path.join(dest_dir, "verify-report.zip")
    try:
        subprocess.run(
            [
                "gh", "run", "download", run_id,
                "-n", "verify-report",
                "-D", dest_dir,
                "--repo", os.environ["GITHUB_REPOSITORY"],
            ],
            capture_output=True,
            text=True,
            check=True,
        )
    except subprocess.CalledProcessError:
        print(f"WARN run {run_id}: verify-report artifact not available (expired?)")
        return None
    # gh run download extracts the artifact contents (report.json) into dest_dir
    report = os.path.join(dest_dir, "report.json")
    return report if os.path.exists(report) else None


def load_store(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def write_store(path, store):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(store, f, indent=2)
        f.write("\n")


def merge_issue_detailed(store, issue, packages, run_id):
    """Merge confirmed rotated keys from an issue's report into the store.

    Returns a list of (package, source, action) tuples describing what happened
    so the workflow can produce a review summary.
    """
    rows = []
    with tempfile.TemporaryDirectory() as tmp:
        report_path = download_report(run_id, tmp)
        if not report_path:
            print(f"WARN issue #{issue['number']}: no report, skipping {packages}")
            return rows
        with open(report_path, "r", encoding="utf-8") as f:
            report = json.load(f)
        results = {r.get("package"): r for r in report.get("results", [])}

        for pkg in packages:
            if pkg not in results:
                print(f"WARN issue #{issue['number']}: package {pkg} not in report")
                continue
            r = results[pkg]
            if r.get("status") != "mismatch":
                print(f"WARN issue #{issue['number']}: {pkg} is not a mismatch, skipping")
                continue
            actual = split_fingerprint(r.get("actual"))
            recorded = r.get("recorded") or ""
            source = r.get("source") or "unknown"
            # recorded may be two concatenated chunks; split into individual keys
            cleaned = recorded.replace(":", "").upper()
            old_keys = []
            for i in range(0, max(0, len(cleaned) - 63), 64):
                chunk = cleaned[i : i + 64]
                if len(chunk) == 64:
                    old_keys.append(":".join(chunk[j : j + 2] for j in range(0, 64, 2)))
            if not actual:
                print(f"WARN issue #{issue['number']}: {pkg} has no actual fingerprint")
                continue

            entries = store.setdefault("entries", [])
            existing = next((e for e in entries if e["package"] == pkg), None)
            if existing:
                action = "unchanged"
                if existing["new_key"] != actual or set(existing["old_keys"]) != set(old_keys):
                    existing["new_key"] = actual
                    existing["old_keys"] = old_keys
                    existing["source_issue"] = issue["number"]
                    action = "updated"
                rows.append((pkg, source, action))
            else:
                entries.append(
                    {
                        "package": pkg,
                        "old_keys": old_keys,
                        "new_key": actual,
                        "source_issue": issue["number"],
                    }
                )
                rows.append((pkg, source, "added"))
    return rows


def reconcile_with_data_yml(store, summary_rows=None):
    """Remove store entries whose new key is now recorded in upstream data.yml.

    When upstream adds the rotated key to the verified database, keeping it in
    our rotated-keys list is redundant (and risks users importing a key that is
    now already covered by the internal database). This fetches the current
    data.yml and drops any entry whose new_key is present for that package.
    """
    if not store.get("entries"):
        return 0
    if summary_rows is None:
        summary_rows = []

    try:
        with urllib.request.urlopen(DATA_YML_URL, timeout=30) as resp:
            content = resp.read().decode("utf-8")
    except Exception as e:
        print(f"WARN could not fetch data.yml for reconciliation: {e}")
        return 0

    # Build package -> set of recorded fingerprints from data.yml.
    # data.yml uses `  - package: <pkg>` then `      - fingerprint: <fp>` with
    # optional `|-` block scalars wrapping long fingerprints over lines.
    package_fps = {}
    current_pkg = None
    in_block = False
    block_fp = ""
    lines = content.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        pkg_match = re.match(r"^-\s+package:\s*(.+)$", stripped)
        if pkg_match:
            current_pkg = pkg_match.group(1).strip()
            package_fps.setdefault(current_pkg, set())
            in_block = False
            i += 1
            continue
        fp_line = re.match(r"^-\s+fingerprint:\s*(.*)$", stripped)
        if fp_line and current_pkg is not None:
            fp_val = fp_line.group(1).strip()
            if fp_val.endswith("|-"):
                in_block = True
                block_fp = ""
            elif fp_val:
                package_fps[current_pkg].add(normalize_hex(fp_val))
            i += 1
            continue
        if in_block and current_pkg is not None:
            # block scalar content: fingerprint continuation lines
            if stripped == "" and block_fp.rstrip(":").replace(":", "").replace(" ", ""):
                pass
            # a fingerprint continuation line is hex-with-colons
            if re.match(r"^[0-9A-Fa-f:]+$", stripped):
                block_fp += stripped
                # if we now have a complete sha256 length, record it
                if len(block_fp.replace(":", "")) == 64:
                    package_fps[current_pkg].add(normalize_hex(block_fp))
                    block_fp = ""
                    in_block = False
            elif stripped.startswith("-") or stripped.startswith(":"):
                # next source/list item ends the block
                in_block = False
            i += 1
            continue
        i += 1

    removed = []
    keep = []
    for entry in store["entries"]:
        pkg = entry["package"]
        new_key = entry.get("new_key")
        recorded = package_fps.get(pkg, set())
        new_hex = normalize_hex(new_key) if new_key else ""
        if new_hex and new_hex in recorded:
            removed.append(pkg)
        else:
            keep.append(entry)
    if removed:
        store["entries"] = keep
        for pkg in removed:
            print(f"RECONCILED removed {pkg} (new key now in upstream data.yml)")
            summary_rows.append((pkg, "removed (in upstream)", "", "", "upstream data.yml"))
    else:
        print("RECONCILED no entries cleared by upstream data.yml")
    return len(removed)


def normalize_hex(fp):
    if not fp:
        return ""
    return fp.replace(":", "").strip().upper()


def main():
    store = load_store(STORE_PATH)
    issues = get_labeled_issues()
    print(f"FOUND {len(issues)} rotatedkey-labeled issues")
    summary_rows = []  # (package, action, issue, run, source)
    total_added = 0
    for issue in issues:
        packages = extract_confirmed_packages(issue)
        if not packages:
            print(f"issue #{issue['number']}: no confirmed packages in comment, skipped")
            continue
        run_id = get_run_id(issue)
        if not run_id:
            print(f"WARN issue #{issue['number']}: no workflow run url, skipping")
            continue
        for pkg, source, action in merge_issue_detailed(store, issue, packages, run_id):
            summary_rows.append((pkg, action, issue["number"], run_id, source))
            if action in ("added", "updated"):
                total_added += 1

    total_removed = reconcile_with_data_yml(store, summary_rows)

    # sort entries by package and write
    store["entries"].sort(key=lambda e: e["package"])
    write_store(STORE_PATH, store)

    print("TOTAL_ADDED %d" % total_added)
    print("TOTAL_REMOVED %d" % total_removed)
    print("STORE_ENTRIES %d" % len(store["entries"]))
    print()
    print("SUMMARY_BEGIN")
    print("| Package | Action | Issue | Workflow run | Source |")
    print("|---|---|---|---|---|")
    for row in summary_rows:
        pkg, action, issue, run_id, source = row
        issue_cell = ("#%s" % issue) if issue else "-"
        run_cell = ("[%s](https://github.com/%s/actions/runs/%s)" % (
            run_id, os.environ.get("GITHUB_REPOSITORY", ""), run_id)) if run_id else "-"
        print("| `%s` | %s | %s | %s | %s |" % (pkg, action, issue_cell, run_cell, source))
    print("SUMMARY_END")


if __name__ == "__main__":
    main()
