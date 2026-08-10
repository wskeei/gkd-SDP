#!/usr/bin/env python3
"""Enforce behavioral test quality for Task 26."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


REPLACED_SOURCE_CONTRACT_FILES = {
    "UsageGuardCountdownOverlayLeaseContractTest.kt",
    "UsageGuardCountdownOverlayScreenshotModeContractTest.kt",
    "UsageGuardRequestLayoutContractTest.kt",
    "UsageGuardReviewStateContractTest.kt",
}
FORBIDDEN_PATTERNS = [
    re.compile(r"\bThread\.sleep\b"),
    re.compile(r"\bHttpURLConnection\b"),
    re.compile(r"\bOkHttpClient\b"),
    re.compile(r"java\.net\.http\.HttpClient"),
]


def walk_kotlin(root: Path):
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def violations_for_directory(root: Path, repo_root: Path) -> list[str]:
    failures: list[str] = []
    for path in walk_kotlin(root):
        try:
            rel = path.relative_to(repo_root)
        except ValueError:
            rel = path
        name = path.name
        if name.startswith("Example") or name in {"ExampleUnitTest.kt", "ExampleInstrumentedTest.kt"}:
            failures.append(f"placeholder test is not allowed: {rel}")
        text = path.read_text(encoding="utf-8")
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(text):
                failures.append(f"forbidden test runtime/network pattern: {rel}")
        if name in REPLACED_SOURCE_CONTRACT_FILES:
            if "sourceFile(" in text or "readText()" in text:
                failures.append(f"source-string contract was not replaced: {rel}")
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=None, help="Repository root override for tests")
    args = parser.parse_args(argv)
    root = Path(args.root) if args.root else Path(__file__).resolve().parents[1]
    failures = violations_for_directory(root / "app/src/test", root) + violations_for_directory(
        root / "app/src/androidTest", root
    )
    if failures:
        print("\n".join(sorted(set(failures))), file=sys.stderr)
        return 1
    print("Test quality policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
