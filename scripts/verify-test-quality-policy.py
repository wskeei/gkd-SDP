#!/usr/bin/env python3
"""Enforce behavioral test quality for Task 26."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


FORBIDDEN_PATTERNS = [
    re.compile(r"\bThread\.sleep\b"),
    re.compile(r"\bHttpURLConnection\b"),
    re.compile(r"\bOkHttpClient\b"),
    re.compile(r"java\.net\.http\.HttpClient"),
    re.compile(r"sourceFile\([^)]*\.kt[^)]*\)"),
    re.compile(r"src/main/kotlin[^\n]*\.readText\(\)"),
    re.compile(r"Class\.forName\("),
]

ASSERTION_PATTERN = re.compile(
    r"\b(assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|"
    r"assertSame|assertNotSame|assertThrows|assertArrayEquals|assertNotEquals|"
    r"expectContains|expectClean|expectNoIssues|check\(|require\(|"
    r"onNode\(|performClick\(|performTextInput\(|performScrollTo\(|"
    r"createAndroidComposeRule|ActivityScenario|UiDevice)\b"
)
EMPTY_TEST_PATTERN = re.compile(
    r"@Test\s+fun\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*\{\s*(?://[^\n]*\n\s*)*\}",
    re.MULTILINE,
)


def walk_kotlin(root: Path):
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def violations_for_directory(root: Path, repo_root: Path) -> list[str]:
    failures: list[str] = []
    ui_named = {
        "AppNavigationTest.kt",
        "NavigationRestoreTest.kt",
        "CapabilityFlowTest.kt",
        "SettingsSearchTest.kt",
        "DataDeletionFlowTest.kt",
        "UsageRequestFlowTest.kt",
        "EncryptedBackupFlowTest.kt",
        "ReviewDashboardFlowTest.kt",
        "AccessibilitySmokeTest.kt",
    }
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
        if EMPTY_TEST_PATTERN.search(text):
            failures.append(f"empty @Test body is not allowed: {rel}")
        if "@Test" in text and not ASSERTION_PATTERN.search(text):
            failures.append(f"test file has @Test but no assertion/UI operation: {rel}")
        if name in ui_named and root.name == "androidTest":
            if not re.search(r"onNode\(|performClick\(|performTextInput\(|createAndroidComposeRule|ActivityScenario|UiDevice", text):
                failures.append(f"UI flow test has no real Activity/Compose operation: {rel}")
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
