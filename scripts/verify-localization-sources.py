#!/usr/bin/env python3
"""Reject unmarked CJK literals in production Kotlin.

UI-facing literals should be Android string resources. A few literals are
intentionally non-display data: rule/heuristic matching text, legacy pure
policy fallbacks used only by tests, preview fixtures, log/error identifiers,
and search keywords. Those must be marked with ``// i18n-ignore: <reason>``.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/kotlin"
CJK = re.compile(r"[\u3400-\u9fff]")
STRING_LITERAL = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')


def has_cjk_string(line: str) -> bool:
    for match in STRING_LITERAL.finditer(line):
        if CJK.search(match.group(1)):
            return True
    return False


def verify_file(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    errors: list[str] = []
    previous_line = ""
    for index, line in enumerate(lines, start=1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            previous_line = line
            continue
        if not has_cjk_string(line):
            previous_line = line
            continue
        if "i18n-ignore" in line or "i18n-ignore" in previous_line:
            previous_line = line
            continue
        try:
            display = path.relative_to(ROOT)
        except ValueError:
            display = path.name
        errors.append(f"{display}:{index}: unmarked CJK literal")
        previous_line = line
    return errors


def main() -> int:
    errors: list[str] = []
    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        errors.extend(verify_file(path))
    if errors:
        print("Localization source violations:")
        print("\n".join(errors))
        return 1
    print("Localization sources: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
