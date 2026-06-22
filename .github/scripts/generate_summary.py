import json
import sys
from collections import defaultdict

with open(sys.argv[1]) as f:
    data = json.load(f)
s = data['stats']
results = data['results']

print('### Database Verification Results')
print('')
print('| Result | Count |')
print('|---|---|')
print(f'| Matches | {s["match"]} |')
print(f'| Mismatches | {s["mismatch"]} |')
print(f'| Errors | {s["error"]} |')
print('')

# group by package for cross-source analysis
by_pkg = defaultdict(list)
for r in results:
    by_pkg[r['package']].append(r)

def is_real_failure(r):
    return r['status'] == 'mismatch' or (
        r['status'] == 'error'
        and 'PAID APP' not in (r.get('error') or '')
        and not (r.get('error') or '').startswith('unsupported source type')
    )

# partial-source matches: package with multiple sources where only some matched
partial = []
for pkg, rows in by_pkg.items():
    matched = [r for r in rows if r['status'] == 'match']
    failed = [r for r in rows if is_real_failure(r)]
    if matched and failed:
        partial.append((pkg, matched))

if partial:
    print(f'**{len(partial)} packages matched on only some sources (not all sources could be verified).**')
    print('')
    for pkg, matched in partial:
        sources = ', '.join(r['source'] for r in matched)
        print(f'- {pkg}: matched via {sources}')
    print('')

# multi-signer details
multi = [r for r in results if r['status'] == 'match' and r.get('multi_signer')]
if multi:
    print(f'**{len(multi)} matches are multi-signer (app signed by one of several recorded certificates).**')
    print('')
    for r in multi:
        chunk = r.get('matched_chunk')
        label = f' signer #{chunk + 1}' if chunk is not None else ''
        print(f'- {r["package"]} ({r["source"]}): matched{label}')
    print('')

if s['mismatch'] > 0:
    print('### Mismatches')
    print('')
    print('| Package | Source | Recorded | Actual |')
    print('|---|---|---|---|')
    for r in results:
        if r['status'] == 'mismatch':
            rfp = r['recorded'][:23] + '...' + r['recorded'][-5:]
            afp = (r['actual'] or 'N/A')[:23] + '...' + (r['actual'] or '')[-5:]
            print(f'| {r["package"]} | {r["source"]} | {rfp} | {afp} |')

if s['error'] > 0:
    paid = [r for r in results if r['status'] == 'error' and 'PAID APP' in (r.get('error') or '')]
    other_errors = [r for r in results if r['status'] == 'error' and 'PAID APP' not in (r.get('error') or '')]
    if paid:
        print('### Paid Apps (skipped — require purchase)')
        print('')
        for r in paid:
            print(f'- {r["package"]} ({r["source"]})')
    if other_errors:
        print('### Errors')
        print('')
        for r in other_errors:
            print(f'- **{r["package"]}** ({r["source"]}): {r["error"]}')
