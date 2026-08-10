#!/usr/bin/env python3
"""Generate app/src/main/res/values-en/strings.xml from translation chunks."""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/values-en/strings.xml"
CHUNKS = [
    "scripts/en_translations_part1.py",
    "scripts/en_translations_part2.py",
    "scripts/en_translations_part3.py",
]

translations: dict[str, str] = {}
for chunk in CHUNKS:
    ns: dict[str, str] = {}
    code = compile(open(ROOT / chunk, encoding="utf-8").read(), chunk, "exec")
    exec(code, {"TRANSLATIONS": ns})
    translations.update(ns)

# cross-check against zh keys
zh = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
zh_keys = set(re.findall(r'<string name="([^"]+)"', zh))
missing = sorted(zh_keys - set(translations))
extra = sorted(set(translations) - zh_keys)
if missing:
    print("MISSING ZH KEYS:", missing)
    sys.exit(1)
if extra:
    print("EXTRA EN KEYS:", extra)
    sys.exit(1)

def needs_format(value: str) -> bool:
    return "%" in value

def escape_xml(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "\\'")
    )

lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
for key in sorted(translations):
    value = translations[key]
    escaped = escape_xml(value)
    if needs_format(value):
        lines.append(f'    <string name="{key}">{escaped}</string>')
    else:
        lines.append(f'    <string name="{key}" formatted="false">{escaped}</string>')
lines.append("</resources>")
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote {OUT} with {len(translations)} strings")
