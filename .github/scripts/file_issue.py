import json, os, yaml
from collections import defaultdict

report = os.environ['RUNNER_TEMP'] + '/report.json'
run_url = os.environ['RUN_URL']
curr_data = os.environ.get('CURRENT_DATA', '')
db_pkgs_file = os.environ.get('DB_PACKAGES', '')

upstream_pkgs = set()
upstream_pkg_map = {}
if curr_data and os.path.isfile(curr_data):
    with open(curr_data) as f:
        raw = yaml.safe_load(f)
    if isinstance(raw, dict):
        raw = raw.get('packages', [])
    for p in raw:
        pkg = p.get('package', '')
        if pkg:
            upstream_pkgs.add(pkg)
            upstream_pkg_map[pkg] = p

def first_issue(pkg_data):
    for sig in pkg_data.get('signature', []):
        for src in sig.get('sources', []):
            issue = src.get('issue', '')
            if issue:
                return issue
    return ''

db_pkgs = set()
if db_pkgs_file and os.path.isfile(db_pkgs_file):
    with open(db_pkgs_file) as f:
        db_pkgs = {line.strip() for line in f if line.strip()}

if not upstream_pkgs:
    print('skip')
    exit(0)

removed = sorted(db_pkgs - upstream_pkgs)

with open(report) as f:
    d = json.load(f)

by_pkg = defaultdict(list)
for r in d['results']:
    by_pkg[r['package']].append(r)

trigger_mismatches = []
trigger_errors = []
partial = []
real_fail = []

def is_real_failure(r):
    return r['status'] == 'mismatch' or (
        r['status'] == 'error'
        and 'PAID APP' not in (r.get('error') or '')
        and not (r.get('error') or '').startswith('unsupported source type')
    )

for pkg, rows in by_pkg.items():
    matched = [r for r in rows if r['status'] == 'match']
    failed = [r for r in rows if is_real_failure(r)]
    if matched and failed:
        partial.append((pkg, matched, failed))
    elif not matched and failed:
        real_fail.append((pkg, failed))

trigger_stale = len(removed)

lines = ['Automated spot-check verification found issues in the database.']

if real_fail:
    lines += ['',
              '**Packages with no matching source:**',
              '']
    for pkg, failed in real_fail:
        for r in failed:
            if r['status'] == 'mismatch':
                rec = (r['recorded'] or '')[:23] + '...' + (r['recorded'] or '')[-5:]
                act = (r.get('actual') or 'N/A')[:23] + '...' + (r.get('actual') or 'N/A')[-5:]
                lines.append(f'- **{pkg}** ({r["source"]}): recorded {rec}, actual {act}')
                trigger_mismatches.append(r)
            elif r['status'] == 'error' and 'PAID APP' not in (r.get('error') or ''):
                lines.append(f'- **{pkg}** ({r["source"]}): {r["error"]}')
                trigger_errors.append(r)

if partial:
    lines += ['',
              '**Packages that matched on some sources (noted, not counted as failures):**',
              '']
    for pkg, matched, failed in partial:
        ok = ', '.join(r['source'] for r in matched)
        for r in failed:
            if r['status'] == 'mismatch':
                rec = (r['recorded'] or '')[:23] + '...' + (r['recorded'] or '')[-5:]
                act = (r.get('actual') or 'N/A')[:23] + '...' + (r.get('actual') or 'N/A')[-5:]
                lines.append(f'- {pkg} ({r["source"]}): recorded {rec}, actual {act} (matched via {ok})')
            elif r['status'] == 'error' and 'PAID APP' not in (r.get('error') or ''):
                lines.append(f'- {pkg} ({r["source"]}): {r["error"]} (matched via {ok})')

if removed:
    lines += ['',
              '**Packages removed from upstream database (stale entries):**',
              '']
    for pkg in removed:
        entry = upstream_pkg_map.get(pkg)
        issue = f' ({first_issue(entry)})' if entry and first_issue(entry) else ''
        lines.append(f'- {pkg}{issue}')

paid_count = sum(1 for r in d['results'] if r['status'] == 'error' and 'PAID APP' in (r.get('error') or ''))
if paid_count:
    lines += ['', f'{paid_count} paid app(s) skipped (require purchase).']

if not trigger_mismatches and not trigger_errors and not trigger_stale:
    print('skip')
    exit(0)

lines += ['',
          f'Workflow run: {run_url}',
          'Download the verify-report artifact for full details.']

print(len(trigger_mismatches))
print(len(trigger_errors))
print(trigger_stale)
print('\n'.join(lines))
