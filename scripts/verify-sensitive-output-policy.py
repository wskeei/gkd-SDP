#!/usr/bin/env python3

import argparse
import re
from pathlib import Path


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
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//.*", "", source)


def verify(root: Path) -> list[str]:
    source_root = root / "app/src/main/kotlin"
    if not source_root.exists():
        return []

    failures: list[str] = []
    for path in sorted(source_root.rglob("*.kt")):
        source = without_comments(path.read_text(encoding="utf-8"))
        relative_path = path.relative_to(root)
        for pattern, description in FORBIDDEN_PATTERNS:
            for match in pattern.finditer(source):
                line = source.count("\n", 0, match.start()) + 1
                failures.append(f"{relative_path}:{line}: {description}")
        for match in SENSITIVE_LEGACY_ARGUMENT.finditer(source):
            line = source.count("\n", 0, match.start()) + 1
            failures.append(f"{relative_path}:{line}: sensitive LogUtils argument")
        for match in THROWABLE_UI.finditer(source):
            line = source.count("\n", 0, match.start()) + 1
            failures.append(
                f"{relative_path}:{line}: Throwable detail in user interface"
            )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()

    failures = verify(args.root.resolve())
    if failures:
        print("Sensitive output policy violations:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("Sensitive output policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
