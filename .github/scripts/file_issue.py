import json, os
from collections import defaultdict

report = os.environ['RUNNER_TEMP'] + '/report.json'
run_url = os.environ['RUN_URL']

with open(report) as f:
    d = json.load(f)

by_pkg = defaultdict(list)
for r in d['results']:
    by_pkg[r['package']].append(r)

trigger_mismatches = []
trigger_errors = []
partial = []
real_fail = []

TRANSIENT_ERRORS = (
    'could not fetch',
    'could not resolve',
    'couldn\'t resolve',
    'empty file',
    'file is empty',
    'apk is empty',
    'connection refused',
    'connection timed out',
    'operation timed out',
    'failed to connect',
    'network is unreachable',
    'name or service not known',
    'getaddrinfo failed',
    'no route to host',
    'reset by peer',
    'broken pipe',
    'the read operation timed out',
    'the write operation timed out',
    'remote end closed connection',
)

def is_transient(err):
    lower = err.lower()
    return any(p in lower for p in TRANSIENT_ERRORS)

def is_real_failure(r):
    if r['status'] == 'mismatch':
        return True
    if r['status'] != 'error':
        return False
    err = r.get('error') or ''
    return not (
        'PAID APP' in err
        or 'not purchased' in err.lower()
        or 'no download url' in err.lower()
        or err.startswith('unsupported source type')
        or is_transient(err)
    )

for pkg, rows in by_pkg.items():
    matched = [r for r in rows if r['status'] == 'match']
    failed = [r for r in rows if is_real_failure(r)]
    if matched and failed:
        partial.append((pkg, matched, failed))
    elif not matched and failed:
        real_fail.append((pkg, failed))

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

paid_count = sum(1 for r in d['results'] if r['status'] == 'error' and 'PAID APP' in (r.get('error') or ''))
if paid_count:
    lines += ['', f'{paid_count} paid app(s) skipped (require purchase).']

if not trigger_mismatches and not trigger_errors:
    print('skip')
    exit(0)

lines += ['',
          f'Workflow run: {run_url}',
          'Download the verify-report artifact for full details.']

print(len(trigger_mismatches))
print(len(trigger_errors))
print('\n'.join(lines))
