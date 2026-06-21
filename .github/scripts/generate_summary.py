import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)
s = data['stats']
print('### Database Verification Results')
print('')
print('| Result | Count |')
print('|---|---|')
print(f'| Matches | {s["match"]} |')
print(f'| Mismatches | {s["mismatch"]} |')
print(f'| Errors | {s["error"]} |')
print('')
if s['match'] > 0:
    multi = [r for r in data['results'] if r['status'] == 'match' and r.get('multi_signer')]
    if multi:
        print(f'**{len(multi)} matches are multi-signer apps (matches one of several recorded fingerprints).**')
        print('')
if s['mismatch'] > 0:
    print('### Mismatches')
    print('')
    print('| Package | Source | Recorded | Actual |')
    print('|---|---|---|---|')
    for res in data['results']:
        if res['status'] == 'mismatch':
            rfp = res['recorded'][:23] + '...' + res['recorded'][-5:]
            afp = (res['actual'] or 'N/A')[:23] + '...' + (res['actual'] or '')[-5:]
            label = ' (multi-signer)' if res.get('multi_signer') else ''
            print(f'| {res["package"]}{label} | {res["source"]} | {rfp} | {afp} |')
if s['error'] > 0:
    paid = [r for r in data['results'] if r['status'] == 'error' and 'PAID APP' in (r.get('error') or '')]
    other_errors = [r for r in data['results'] if r['status'] == 'error' and 'PAID APP' not in (r.get('error') or '')]
    if paid:
        print('### Paid Apps (skipped — require purchase)')
        print('')
        for res in paid:
            print(f'- {res["package"]} ({res["source"]})')
    if other_errors:
        print('### Errors')
        print('')
        for res in other_errors:
            print(f'- **{res["package"]}** ({res["source"]}): {res["error"]}')
