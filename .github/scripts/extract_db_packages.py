import json, os, re

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
    """Extract all fingerprint strings from Hashes blocks in an entry."""
    fps = []
    hashes_marker = 'fingerprints = listOf('
    idx = 0
    while True:
        pos = entry_text.find(hashes_marker, idx)
        if pos == -1:
            break
        start = pos + len(hashes_marker)
        end = extract_balanced(entry_text, start)
        chunk = entry_text[start:end - 1]
        idx = end
        for m in re.finditer(r'"([^"]+)"', chunk):
            fps.append(m.group(1))
    return fps

path = 'app/src/main/kotlin/dev/soupslurpr/appverifier/InternalVerificationInfoDatabase.kt'
with open(path) as f:
    text = f.read()

pkgs_all = []
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
    pkgs_all.append((pkg, has_pg))
    if has_pg:
        fp_map[pkg] = extract_fingerprints(entry_text)

runner_temp = os.environ['RUNNER_TEMP']
with open(runner_temp + '/db_packages.txt', 'w') as out:
    for p, _ in sorted(pkgs_all):
        out.write(p + '\n')
with open(runner_temp + '/pg_packages.txt', 'w') as out:
    for p, has_pg in sorted(pkgs_all):
        if has_pg:
            out.write(p + '\n')
with open(runner_temp + '/pg_fingerprints.json', 'w') as out:
    json.dump(fp_map, out, indent=2)
total = len(pkgs_all)
pg_count = sum(1 for _, h in pkgs_all if h)
print(f'{total} packages ({pg_count} with PG sources)')
