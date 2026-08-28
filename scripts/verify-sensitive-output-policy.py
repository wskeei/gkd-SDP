#!/usr/bin/env python3
"""Reject sensitive logging and throwable details in newly changed app code.

The v2.1.0 baseline contains historical logging that predates this policy. The
check therefore examines added production lines relative to the immutable
v2.1.0 tag when git history is available. A standalone temporary tree is
scanned in full, which keeps the checker directly testable.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
import subprocess
import sys


FORBIDDEN_PATTERNS = (
    (re.compile(r"\bimport\s+android\.util\.Log\b"), "android.util.Log"),
    (re.compile(r"\bLog\.(?:d|e|i|v|w|wtf|getStackTraceString)\s*\("), "android.util.Log"),
    (re.compile(r"\.printStackTrace\s*\("), "printStackTrace"),
    (re.compile(r"\.stackTraceToString\s*\("), "stackTraceToString"),
    (re.compile(r"(?<![A-Za-z])println\s*\("), "println"),
)

SENSITIVE_LEGACY_ARGUMENT = re.compile(
    r"LogUtils\.d\s*\(\s*(?:"
    r"[A-Za-z_][A-Za-z0-9_]*(?:intent|bundle|uri|node|contact|reason)"
    r"|intent|bundle|uri|node|contact|reason(?:Text)?"
    r"|request\.url|call\.request\.uri"
    r")\b",
    re.IGNORECASE,
)

THROWABLE_UI = re.compile(
    r"\b(?:toast|Snackbar|Dialog)\s*\([^\n]{0,500}"
    r"(?:\.message\b|stackTraceToString\s*\()",
    re.IGNORECASE,
)


def without_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", lambda match: "\n" * match.group(0).count("\n"), source, flags=re.DOTALL)
    return re.sub(r"//.*", "", source)


def changed_line_numbers(root: Path, baseline_ref: str) -> dict[str, set[int]] | None:
    if not (root / ".git").exists():
        return None
    ref_check = subprocess.run(
        ["git", "rev-parse", "--verify", baseline_ref],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    if ref_check.returncode != 0:
        return None
    diff = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", baseline_ref, "--", "app/src/main/kotlin"],
        cwd=root,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    changed: dict[str, set[int]] = {}
    current_path: str | None = None
    new_line = 0
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current_path = line[6:]
            changed.setdefault(current_path, set())
            continue
        if line.startswith("@@"):
            match = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if match:
                new_line = int(match.group(1))
            continue
        if current_path is None or not line:
            continue
        if line.startswith("+"):
            changed[current_path].add(new_line)
            new_line += 1
        elif line.startswith("-"):
            continue
        else:
            new_line += 1
    return changed


def verify(root: Path, baseline_ref: str = "v2.1.0") -> list[str]:
    source_root = root / "app/src/main/kotlin"
    if not source_root.exists():
        return []

    changed = changed_line_numbers(root, baseline_ref)
    failures: list[str] = []
    for path in sorted(source_root.rglob("*.kt")):
        relative = str(path.relative_to(root))
        source = without_comments(path.read_text(encoding="utf-8"))
        allowed_lines = None if changed is None else changed.get(relative, set())
        if changed is not None and not allowed_lines:
            continue

        def is_relevant(match: re.Match[str]) -> bool:
            line = source.count("\n", 0, match.start()) + 1
            return allowed_lines is None or line in allowed_lines

        for pattern, description in FORBIDDEN_PATTERNS:
            for match in pattern.finditer(source):
                if is_relevant(match):
                    line = source.count("\n", 0, match.start()) + 1
                    failures.append(f"{relative}:{line}: {description}")
        for match in SENSITIVE_LEGACY_ARGUMENT.finditer(source):
            if is_relevant(match):
                line = source.count("\n", 0, match.start()) + 1
                failures.append(f"{relative}:{line}: sensitive LogUtils argument")
        for match in THROWABLE_UI.finditer(source):
            if is_relevant(match):
                line = source.count("\n", 0, match.start()) + 1
                failures.append(f"{relative}:{line}: Throwable detail in user interface")
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline-ref", default="v2.1.0")
    args = parser.parse_args(argv)

    failures = verify(args.root.resolve(), args.baseline_ref)
    if failures:
        print("Sensitive output policy violations:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    print("Sensitive output policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
