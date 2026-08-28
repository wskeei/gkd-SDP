#!/usr/bin/env python3
"""Reject new unmarked CJK UI literals in v2.1.0 production changes."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys


CJK = re.compile(r"[\u3400-\u9fff]")
STRING_LITERAL = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')


def has_cjk_string(line: str) -> bool:
    return any(CJK.search(match.group(1)) for match in STRING_LITERAL.finditer(line))


def verify_file(path: Path) -> list[str]:
    errors: list[str] = []
    previous_line = ""
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            previous_line = line
            continue
        if has_cjk_string(line) and "i18n-ignore" not in line and "i18n-ignore" not in previous_line:
            errors.append(f"{path}:{index}: unmarked CJK literal")
        previous_line = line
    return errors


def changed_source_errors(root: Path, baseline_ref: str) -> list[str] | None:
    if not (root / ".git").exists():
        return None
    if subprocess.run(
        ["git", "rev-parse", "--verify", baseline_ref],
        cwd=root,
        capture_output=True,
        check=False,
    ).returncode != 0:
        return None
    diff = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", baseline_ref, "--", "app/src/main/kotlin"],
        cwd=root,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    errors: list[str] = []
    current_path: str | None = None
    current_line = 0
    previous_line = ""
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current_path = line[6:]
            previous_line = ""
            continue
        if line.startswith("@@"):
            match = re.search(r"\+(\d+)", line)
            if match:
                current_line = int(match.group(1))
            previous_line = ""
            continue
        if current_path is None or not line:
            continue
        if line.startswith("+"):
            added = line[1:]
            if has_cjk_string(added) and "i18n-ignore" not in added and "i18n-ignore" not in previous_line:
                errors.append(f"{current_path}:{current_line}: unmarked CJK literal")
            previous_line = added
            current_line += 1
        elif line.startswith("-"):
            continue
        else:
            previous_line = line[1:] if line.startswith(" ") else line
            current_line += 1
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline-ref", default="v2.1.0")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    changed_errors = changed_source_errors(root, args.baseline_ref)
    if changed_errors is None:
        source_root = root / "app/src/main/kotlin"
        errors = [error for path in sorted(source_root.rglob("*.kt")) for error in verify_file(path)]
    else:
        errors = changed_errors
    if errors:
        print("Localization source violations:", file=sys.stderr)
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Localization sources: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
