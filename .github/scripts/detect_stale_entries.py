import json, os, re, sys, yaml


def extract_balanced(text, start):
    depth = 1
    i = start
    while i < len(text):
        c = text[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i + 1
        elif c in "'`":
            delim = c
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == delim:
                    break
                i += 1
        i += 1
    return len(text)


def extract_fingerprints(entry_text):
    return re.findall(r'"((?:[0-9A-F]{2}:)+[0-9A-F]{2})"', entry_text)


def first_issue(pkg_data):
    for sig in pkg_data.get('signature', []):
        for src in sig.get('sources', []):
            issue = src.get('issue', '')
            if issue:
                return issue
    return ''


def short_fp(fp):
    return fp[:23] + '...' + fp[-5:] if len(fp) > 30 else fp


db_path = 'app/src/main/kotlin/dev/soupslurpr/appverifier/InternalVerificationInfoDatabase.kt'
upstream_path = os.environ.get('CURRENT_DATA', '')
run_url = os.environ.get('RUN_URL', '')
dry_run = os.environ.get('DRY_RUN', 'false')

if not upstream_path or not os.path.isfile(upstream_path):
    print('skip')
    exit(0)

with open(upstream_path) as f:
    raw = yaml.safe_load(f)
if isinstance(raw, dict):
    raw = raw.get('packages', [])

upstream_pkgs = {}
for p in raw:
    pkg = p.get('package', '')
    if pkg:
        upstream_pkgs[pkg] = p

if not upstream_pkgs:
    print('skip')
    exit(0)

with open(db_path) as f:
    text = f.read()

pg_pkgs = []
fp_map = {}
idx = 0
marker = 'InternalDatabaseVerificationInfo('
while True:
    pos = text.find(marker, idx)
    if pos == -1:
        break
    content_start = pos + len(marker)
    entry_end = extract_balanced(text, content_start)
    entry_text = text[pos:entry_end]
    idx = entry_end
    m = re.search(r'"([^"]+)"', entry_text)
    if not m:
        continue
    pkg = m.group(1)
    has_pg = bool(re.search(r'Source\.(?!APPVERIFIER)\b', entry_text))
    if has_pg:
        pg_pkgs.append(pkg)
        fp_map[pkg] = extract_fingerprints(entry_text)

stale = sorted(set(pg_pkgs) - set(upstream_pkgs.keys()))

if not stale:
    print('skip')
    exit(0)

body_lines = [
    'Automated stale-entry check found packages in the database that have been removed from the upstream verified-apps list.',
    '',
    '**Stale entries (no longer in upstream data.yml):**',
    '',
]
for pkg in stale:
    entry = upstream_pkgs.get(pkg)
    issue = f' ({first_issue(entry)})' if entry and first_issue(entry) else ''
    fps = fp_map.get(pkg, [])
    fp_display = ', '.join(short_fp(fp) for fp in fps) if fps else ''
    suffix = f' — {fp_display}' if fp_display else ''
    body_lines.append(f'- {pkg}{issue}{suffix}')

if run_url:
    body_lines += ['', f'Workflow run: {run_url}']

print(len(stale))
print('\n'.join(body_lines))
