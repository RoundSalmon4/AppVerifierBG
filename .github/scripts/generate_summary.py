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
if s['mismatch'] > 0:
    print('### Mismatches')
    print('')
    print('| Package | Source | Recorded | Actual |')
    print('|---|---|---|---|')
    for res in data['results']:
        if res['status'] == 'mismatch':
            rfp = res['recorded'][:23] + '...'
            afp = (res['actual'] or 'N/A')[:23] + '...'
            print(f'| {res["package"]} | {res["source"]} | {rfp} | {afp} |')
if s['error'] > 0:
    print('### Errors')
    print('')
    for res in data['results']:
        if res['status'] == 'error':
            print(f'- **{res["package"]}** ({res["source"]}): {res["error"]}')
