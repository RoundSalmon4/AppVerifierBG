#!/usr/bin/env python3
"""Generate InternalVerificationInfoDatabase.kt from privacyguides/verified-apps data.yml."""

import argparse
import re
import sys
import codecs

import yaml

KOTLIN_SOURCE_FILE = (
    "app/src/main/kotlin/dev/soupslurpr/appverifier/InternalVerificationInfoDatabase.kt"
)

SOURCE_MAP = {
    "Accrescent": "ACCRESCENT",
    "Google Play": "GOOGLE_PLAY_STORE",
    "F-Droid": "FDROID",
    "F-Droid (Custom)": "FDROID",
    "F-Droid (IzzyOnDroid)": "FDROID_IZZYONDROID",
    "GitHub": "GITHUB",
    "Codeberg": "CODEBERG",
    "GitLab": "GITLAB",
    "App's F-Droid Repo": "APP_FDROID_REPO",
    "App's Website": "WEBSITE",
    "Google Pixel OS": "GOOGLE_PIXEL_OS",
    "Direct APK Link": "DIRECT_APK_LINK",
    "AppVerifier": "APPVERIFIER",
    "HTTPS Verified Domain": "VERIFIED_DOMAIN_HTTPS",
    "DNS Verified Domain": "VERIFIED_DOMAIN_DNS",
}


def source_name_to_enum(name):
    result = name.strip().upper()
    result = re.sub(r"[^A-Z0-9]", "_", result)
    result = re.sub(r"_+", "_", result)
    result = result.strip("_")
    if not result or result[0].isdigit():
        result = "SOURCE_" + result
    return result


def build_display_name_map(header_text):
    mapping = {}
    for match in re.finditer(r'^\s+(\w+)\(("[^"]*")\)', header_text, re.MULTILINE):
        display_name = match.group(2).strip('"')
        mapping[display_name] = match.group(1)
    return mapping


def kotlin_string_escape(s):
    s = s.replace("\\", "\\\\")
    s = s.replace("$", "\\$")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "\\r")
    s = s.replace("\t", "\\t")
    return s


def find_unknown_sources(privacyguides_data):
    unknown = set()
    for app in privacyguides_data:
        for sig in app.get("signature", []):
            sources = sig.get("sources", [])
            if sources:
                for s in sources:
                    name = s.get("name", "").strip()
                    if name and name not in SOURCE_MAP:
                        unknown.add(name)
            else:
                name = sig.get("source", "").strip()
                if name and name not in SOURCE_MAP:
                    unknown.add(name)
    return sorted(unknown)


def insert_source_enum_values(header, new_enum_names):
    enum_match = re.search(
        r"^(enum class Source\(.*?\)\s*\{)(.*?)(\})",
        header,
        re.MULTILINE | re.DOTALL,
    )
    if not enum_match:
        print("  warning: could not find Source enum in header", file=sys.stderr)
        return header, {}

    existing_enum_text = enum_match.group(2)
    existing_display_map = build_display_name_map(header)

    additions = {}
    for name in new_enum_names:
        if name in existing_display_map:
            additions[name] = existing_display_map[name]
            continue
        enum_val = source_name_to_enum(name)
        unique_val = enum_val
        suffix = 1
        while unique_val in existing_enum_text or unique_val in additions:
            unique_val = f"{enum_val}_{suffix}"
            suffix += 1
        additions[name] = unique_val

    if not additions:
        return header, {}

    new_entries = []
    for name in sorted(additions.keys()):
        if additions[name] in existing_enum_text:
            continue
        new_entries.append(f"    {additions[name]}(\"{kotlin_string_escape(name)}\")")

    if new_entries:
        existing_clean = existing_enum_text.rstrip().rstrip(",")
        new_enum_lines = existing_clean + ",\n" + ",\n".join(new_entries) + ",\n"
        header = header[: enum_match.start(2)] + new_enum_lines + header[enum_match.end(2) :]
    return header, additions


def privacyguides_to_source(name, extra_map=None):
    name = name.strip()
    if name in SOURCE_MAP:
        return "Source." + SOURCE_MAP[name]
    if extra_map and name in extra_map:
        return "Source." + extra_map[name]
    return None


def load_yaml_file(path):
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


def extract_balanced(text, start):
    depth = 1
    i = start
    while i < len(text):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        elif c in "'\"`":
            delim = c
            i += 1
            while i < len(text):
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == delim:
                    break
                i += 1
        i += 1
    return len(text)


def parse_entries(kotlin_text):
    marker = "val internalVerificationInfoDatabase = setOf("
    header_end = kotlin_text.find(marker)
    if header_end == -1:
        return {}, kotlin_text, ""

    header_end += len(marker)
    setof_end = extract_balanced(kotlin_text, header_end)

    header = kotlin_text[:header_end]
    body = kotlin_text[header_end:setof_end]
    footer = kotlin_text[setof_end:]

    entries = {}
    idx = 0
    entry_marker = "InternalDatabaseVerificationInfo("
    while idx < len(body):
        entry_start = body.find(entry_marker, idx)
        if entry_start == -1:
            break
        content_start = entry_start + len(entry_marker)
        entry_end = extract_balanced(body, content_start)
        if entry_end <= content_start:
            break
        line_start = body.rfind("\n", 0, entry_start)
        line_start = 0 if line_start == -1 else line_start + 1
        entry_text = body[line_start:entry_end]
        idx = entry_end

        pkg = extract_package_name(entry_text)
        if pkg:
            entries[pkg] = entry_text

    return entries, header, footer


def extract_package_name(text):
    m = re.search(r'(?<=")[^"]+(?=")', text)
    return m.group(0) if m else None


S4 = "    "
S8 = "        "
S12 = "            "
S16 = "                "
S20 = "                    "


def format_entry(package, signatures, extra_map=None):
    fp_to_sources = {}

    for sig in signatures:
        raw_fingerprint = sig.get("fingerprint", "").strip()
        if not raw_fingerprint:
            continue

        sources = sig.get("sources", [])
        if sources:
            source_names = [s.get("name", "").strip() for s in sources]
        else:
            source_names = [sig.get("source", "").strip()]

        for raw_name in source_names:
            source = privacyguides_to_source(raw_name, extra_map)
            if source is None:
                continue
            for fingerprint in raw_fingerprint.splitlines():
                fingerprint = fingerprint.strip()
                if not fingerprint:
                    continue
                if fingerprint not in fp_to_sources:
                    fp_to_sources[fingerprint] = set()
                fp_to_sources[fingerprint].add(source)

    if not fp_to_sources:
        return None, set()

    source_set_to_fps = {}
    for fingerprint, sources in fp_to_sources.items():
        key = frozenset(sources)
        if key not in source_set_to_fps:
            source_set_to_fps[key] = []
        source_set_to_fps[key].append(fingerprint)

    hashes_blocks = []
    for source_set, fingerprints in sorted(
        source_set_to_fps.items(), key=lambda x: sorted(x[1])[0]
    ):
        sorted_sources = sorted(source_set)
        sorted_fps = sorted(fingerprints)
        source_lines = ",\n".join(f"{S20}{s}" for s in sorted_sources)
        fp_lines = ",\n".join(f'{S20}"{kotlin_string_escape(fp.upper())}"' for fp in sorted_fps)

        hashes_blocks.append(
            f"""{S12}Hashes(
{S16}listOf(
{source_lines}
{S16}),
{S16}listOf(
{fp_lines}
{S16}),
{S16}false
{S12})"""
        )

    if len(fp_to_sources) > 1:
        all_sources: set[str] = set()
        for sources in fp_to_sources.values():
            all_sources.update(sources)
        sorted_sources = sorted(all_sources)
        sorted_all_fps = sorted(fp_to_sources.keys())

        source_lines = ",\n".join(f"{S20}{s}" for s in sorted_sources)
        fp_lines = ",\n".join(f'{S20}"{kotlin_string_escape(fp.upper())}"' for fp in sorted_all_fps)

        hashes_blocks.append(
            f"""{S12}Hashes(
{S16}listOf(
{source_lines}
{S16}),
{S16}listOf(
{fp_lines}
{S16}),
{S16}false
{S12})"""
        )

    joined = ",\n".join(hashes_blocks)

    return (
        f"{S4}InternalDatabaseVerificationInfo(\n"
        f'{S8}"{kotlin_string_escape(package)}",\n'
        f"{S8}listOf(\n"
        f"{joined}\n"
        f"{S8})\n"
        f"{S4})",
        set(fp_to_sources.keys()),
    )


def extract_hashes_blocks(entry_text):
    blocks = []
    idx = 0
    marker = f"{S12}Hashes("
    while True:
        pos = entry_text.find(marker, idx)
        if pos == -1:
            break
        content_start = pos + len(marker)
        block_end = extract_balanced(entry_text, content_start)
        blocks.append(entry_text[pos:block_end])
        idx = block_end
    return blocks


def fingerprint_from_hashes(hashes_text):
    m = re.search(r'listOf\(\s*\n\s*"([A-F0-9:]+)"', hashes_text)
    return m.group(1) if m else None


def insert_preserved_hashes(new_entry_text, preserved_blocks):
    closing = f"\n{S8})"
    pos = new_entry_text.rfind(closing)
    if pos == -1:
        return new_entry_text
    preserved_text = ",\n".join(preserved_blocks)
    return new_entry_text[:pos] + ",\n" + preserved_text + new_entry_text[pos:]


def generate_kotlin(existing_entries, privacyguides_data, header, footer, extra_map=None):
    updated = {}

    pg_packages = set()
    for app in privacyguides_data:
        pkg = app.get("package", "")
        if pkg:
            pg_packages.add(pkg)

    for app in privacyguides_data:
        pkg = app.get("package", "")
        signatures = app.get("signature", [])
        if not pkg or not signatures:
            continue
        new_entry, new_fps = format_entry(pkg, signatures, extra_map)
        if new_entry is None:
            continue

        preserved = []
        seen_fps = set()

        if pkg in existing_entries:
            old_blocks = extract_hashes_blocks(existing_entries[pkg])
            for b in old_blocks:
                fp = fingerprint_from_hashes(b)
                if fp and fp not in new_fps and fp not in seen_fps:
                    preserved.append(b)
                    seen_fps.add(fp)

        if preserved:
            new_entry = insert_preserved_hashes(new_entry, preserved)
        updated[pkg] = new_entry

    for pkg, entry in existing_entries.items():
        if pkg not in pg_packages or pkg not in updated:
            updated[pkg] = entry

    sorted_entries = sorted(updated.items(), key=lambda x: x[0].lower())

    body_lines = []
    for i, (_, entry) in enumerate(sorted_entries):
        text = entry.rstrip()
        if i < len(sorted_entries) - 1:
            text += ","
        body_lines.append(text)

    all_lines = [header]
    all_lines.extend(body_lines)
    all_lines.append(")")
    all_lines.append(footer)
    return "\n".join(all_lines)


SUPPORTED_SCHEMA_VERSIONS = {2, 3, 4}


def check_schema(raw):
    schema = raw.get("schema") if isinstance(raw, dict) else None
    if schema is not None and schema not in SUPPORTED_SCHEMA_VERSIONS:
        print(
            f"  warning: unexpected schema version {schema}, "
            f"expected one of {sorted(SUPPORTED_SCHEMA_VERSIONS)}",
            file=sys.stderr,
        )


def main():
    parser = argparse.ArgumentParser(
        description="Sync InternalVerificationInfoDatabase.kt from privacyguides/verified-apps."
    )
    parser.add_argument(
        "--data-yml",
        metavar="PATH",
        required=True,
        help="Path to attestation-verified privacyguides/verified-apps data.yml",
    )
    args = parser.parse_args()

    privacyguides_data = load_yaml_file(args.data_yml)

    check_schema(privacyguides_data)
    if isinstance(privacyguides_data, dict):
        privacyguides_data = privacyguides_data.get("packages", [])

    with open(KOTLIN_SOURCE_FILE, "rb") as f:
        raw = f.read()
    bom = raw[:4]
    if bom.startswith(codecs.BOM_UTF16_LE):
        encoding = "utf-16-le"
    elif bom.startswith(codecs.BOM_UTF16_BE):
        encoding = "utf-16-be"
    else:
        encoding = "utf-8"
    kotlin_text = raw.decode(encoding).lstrip("\ufeff")

    existing_entries, header, footer = parse_entries(kotlin_text)

    unknown = find_unknown_sources(privacyguides_data)
    if unknown:
        header, extra_map = insert_source_enum_values(header, unknown)
        if extra_map:
            SOURCE_MAP.update(extra_map)
    else:
        extra_map = None

    existing_enum_vals = set(re.findall(r'^\s+(\w+)\(', header, re.MULTILINE))
    missing_enum_vals = [
        (name, enum_val) for name, enum_val in SOURCE_MAP.items()
        if enum_val not in existing_enum_vals
    ]
    if missing_enum_vals:
        body = ""
        for name, enum_val in missing_enum_vals:
                body += f"    {enum_val}(\"{kotlin_string_escape(name)}\"),\n"
        enum_match = re.search(
            r"^(enum class Source\(.*?\)\s*\{)(.*?)(\})",
            header, re.MULTILINE | re.DOTALL,
        )
        if enum_match:
            existing_body = enum_match.group(2).rstrip().rstrip(",")
            new_body = existing_body + ",\n" + body
            header = header[:enum_match.start(2)] + new_body + header[enum_match.end(2):]

    new_kotlin = generate_kotlin(existing_entries, privacyguides_data, header, footer, extra_map)

    with open(KOTLIN_SOURCE_FILE, "w", encoding="utf-8", newline="") as f:
        f.write(new_kotlin)


if __name__ == "__main__":
    main()
