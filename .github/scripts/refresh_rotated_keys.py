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
import tempfile

STORE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "rotated-keys",
    "rotated_keys.json",
)

MAINTAINER = "roundsalmon4"
KEY_MARKER = "# rotated-keys"
PACKAGE_RE = re.compile(r"^\s*package:\s*(.+?)\s*$", re.IGNORECASE)
RUN_URL_RE = re.compile(r"Workflow run:\s*[^\s]+/actions/runs/(\d+)")
VERIFY_WORKFLOW = "verify-database.yml"


def split_fingerprint(fp):
    if not fp:
        return None
    cleaned = fp.replace(":", "").strip().upper()
    if len(cleaned) != 64 or not all(c in "0123456789ABCDEF" for c in cleaned):
        return None
    return ":".join(cleaned[i : i + 2] for i in range(0, 64, 2))


def split_recorded_chunks(fp):
    """Split a concatenated multi-signer fingerprint into 32-byte chunks.

    data.yml can record a single fingerprint made of several concatenated
    signatures (e.g. Google's old key pair for Messaging). Android reports the
    full rotation lineage via `signingCertificateHistory`, so each chunk must be
    kept in the store for the app's containsAll check to pass.
    """
    if not fp:
        return []
    cleaned = fp.replace(":", "").strip().upper()
    if not cleaned or len(cleaned) % 64 != 0 or not all(c in "0123456789ABCDEF" for c in cleaned):
        return []
    return [":".join(cleaned[i : i + 2] for i in range(j, j + 64, 2)) for j in range(0, len(cleaned), 64)]


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
    # Fetch the raw JSON array (no --jq) so comment bodies with newlines do not
    # break parsing.
    out = run_gh(
        [
            "api",
            f"repos/{os.environ['GITHUB_REPOSITORY']}/issues/{issue_number}/comments",
        ]
    )
    comments = json.loads(out)
    return comments


def extract_confirmed_packages(issue):
    confirmed = set()
    comments = get_issue_comments(issue["number"])
    for c in comments:
        if (c.get("user", {}).get("login") or "").lower() != MAINTAINER.lower():
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


def list_verify_runs(limit=20):
    """List recent Verify Database workflow runs, newest first.

    Returns run ids for completed runs only. The store then reflects the
    latest downloaded key sets rather than a report pinned to an issue.
    """
    try:
        out = run_gh(
            [
                "run", "list",
                "--workflow", VERIFY_WORKFLOW,
                "--limit", str(limit),
                "--json", "databaseId,status,conclusion",
            ]
        )
    except subprocess.CalledProcessError:
        print("WARN could not list verify-database runs")
        return []
    runs = json.loads(out)
    return [r["databaseId"] for r in runs if r.get("status") == "completed"]


def resolve_latest_reports(packages, temp_root):
    """Find the newest completed verify report that still lists each package.

    Each package is resolved to the most recent report that carries it as a
    mismatch with a complete `actual_keys` set, walking runs newest-first.
    Packages with no newer covering report are left out so callers fall back
    to the report pinned to the confirmng issue.

    Returns dict package -> {"report": path, "source": str}.
    """
    result = {}
    remaining = set(packages)
    os.makedirs(temp_root, exist_ok=True)
    for run_id in list_verify_runs():
        if not remaining:
            break
        run_dir = os.path.join(temp_root, f"run-{run_id}")
        os.makedirs(run_dir, exist_ok=True)
        report_path = download_report(str(run_id), run_dir)
        if not report_path:
            continue
        with open(report_path, "r", encoding="utf-8") as f:
            report = json.load(f)
        for r in report.get("results", []):
            pkg = r.get("package")
            if pkg not in remaining:
                continue
            if r.get("status") != "mismatch":
                continue
            if not r.get("actual_keys"):
                continue
            result[pkg] = {
                "report": report_path,
                "source": r.get("source") or "unknown",
                "run_id": str(run_id),
            }
            remaining.discard(pkg)
    return result


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


def merge_issue_detailed(store, issue, packages, run_id, latest_reports=None):
    """Merge confirmed rotated keys into the store.

    `latest_reports` is an optional dict package -> {"report", "source"} built
    from the most recent completed verify report that still lists the package
    as a mismatch. When present it is preferred over the report pinned to the
    issue, so the store does not stay stuck on stale key sets.

    Returns a list of (package, source, action, run_id) tuples describing what
    happened so the workflow can produce a review summary.
    """
    rows = []
    with tempfile.TemporaryDirectory() as tmp:
        report_cache = {}
        for pkg in packages:
            latest = (latest_reports or {}).get(pkg)
            report_path = latest["report"] if latest else None
            source = (latest or {}).get("source") or "unknown"
            used_run = (latest or {}).get("run_id")
            if report_path is None:
                # Fall back to the report pinned to the confirming issue.
                report_path = report_cache.get(run_id)
                if report_path is None:
                    report_path = download_report(run_id, tmp)
                    report_cache[run_id] = report_path
                used_run = run_id
            if not report_path:
                print(f"WARN issue #{issue['number']}: no report for {pkg}, skipping")
                continue
            with open(report_path, "r", encoding="utf-8") as f:
                report = json.load(f)
            results = {r.get("package"): r for r in report.get("results", [])}
            r = results.get(pkg)
            if r is None:
                print(f"WARN issue #{issue['number']}: package {pkg} not in report")
                continue
            if r.get("status") != "mismatch":
                print(f"WARN issue #{issue['number']}: {pkg} is not a mismatch, skipping")
                continue
            source = r.get("source") or source
            # The device reports the full rotation lineage (signingCertificate
            # History), not just the certs in the current APK signing block.
            # So the store must hold the union of:
            #   - the recorded fingerprint chunks from data.yml (old keys,
            #     preserved across rotations)
            #   - the actual_keys observed in the downloaded APK (new keys)
            # Merging is always monotonic (union); keys are never removed by a
            # report, so old lineage keys stay available for containsAll.
            recorded_chunks = split_recorded_chunks(r.get("recorded"))
            observed = set(recorded_chunks)
            for k in r.get("actual_keys") or []:
                kk = split_fingerprint(k)
                if kk:
                    observed.add(kk)
            if not observed:
                actual = split_fingerprint(r.get("actual"))
                if actual:
                    observed.add(actual)
            if not observed:
                print(f"WARN issue #{issue['number']}: {pkg} has no actual fingerprint")
                continue

            entries = store.setdefault("entries", [])
            existing = next((e for e in entries if e["package"] == pkg), None)
            if existing:
                action = "unchanged"
                merged = set(existing.get("keys", [])) | observed
                if merged != set(existing.get("keys", [])):
                    existing["keys"] = sorted(merged)
                    existing["source_issue"] = issue["number"]
                    action = "updated"
                rows.append((pkg, source, action, used_run))
            else:
                entries.append(
                    {
                        "package": pkg,
                        "keys": sorted(observed),
                        "source_issue": issue["number"],
                    }
                )
                rows.append((pkg, source, "added", used_run))
    return rows


def read_verified_data_yml():
    """Read the upstream data.yml that rotate-keys.yml fetched with the
    privacyguides/verified-apps action.

    The action only leaves a file at $RUNNER_TEMP/data.yml once its
    attestation is verified. Returns its content, or None if it is missing.
    """
    try:
        with open(os.path.join(os.environ["RUNNER_TEMP"], "data.yml"), encoding="utf-8") as f:
            return f.read()
    except (KeyError, OSError):
        print("WARN no verified data.yml in $RUNNER_TEMP, skipping reconcile")
        return None


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

    content = read_verified_data_yml()
    if content is None:
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
        recorded = package_fps.get(pkg, set())
        keys = entry.get("keys") or ([entry["new_key"]] if entry.get("new_key") else [])
        key_hexes = {normalize_hex(k) for k in keys if k}
        # Remove the entry once upstream data.yml records every key from the
        # rotated APK's signing set, meaning the database has fully caught up.
        if key_hexes and key_hexes.issubset(recorded):
            removed.append(pkg)
        else:
            keep.append(entry)
    if removed:
        store["entries"] = keep
        for pkg in removed:
            print(f"RECONCILED removed {pkg} (all keys now in upstream data.yml)")
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

    # Collect every confirmed package up front so the latest-report lookup can
    # be run once against the newest verify reports instead of per issue.
    confirmed_by_issue = {}
    all_packages = set()
    for issue in issues:
        packages = extract_confirmed_packages(issue)
        if not packages:
            print(f"issue #{issue['number']}: no confirmed packages in comment, skipped")
            continue
        run_id = get_run_id(issue)
        if not run_id:
            print(f"WARN issue #{issue['number']}: no workflow run url, skipping")
            continue
        confirmed_by_issue[issue["number"]] = {
            "packages": packages,
            "fallback_run": run_id,
        }
        all_packages.update(packages)

    latest_reports = {}
    # Keep the temp dir alive through the merge so the downloaded reports
    # remain readable; it is cleaned up once the merge is done.
    with tempfile.TemporaryDirectory() as tmp:
        latest_reports = resolve_latest_reports(all_packages, tmp)
        for issue_num, entry in confirmed_by_issue.items():
            packages = entry["packages"]
            run_id = entry["fallback_run"]
            for pkg, source, action, used_run in merge_issue_detailed(
                store, {"number": issue_num}, packages, run_id, latest_reports
            ):
                summary_rows.append((pkg, action, issue_num, used_run, source))
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
