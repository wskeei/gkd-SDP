#!/usr/bin/env python3
"""Reject placeholder and non-behavioral tests in the v2.1.0 tree."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys


FORBIDDEN_PATTERNS = (
    re.compile(r"\bThread\.sleep\b"),
    re.compile(r"\bHttpURLConnection\b"),
    re.compile(r"\bOkHttpClient\b"),
    re.compile(r"java\.net\.http\.HttpClient"),
    re.compile(r"sourceFile\([^)]*\.kt[^)]*\)"),
    re.compile(r"src/main/kotlin[^\n]*\.readText\(\)"),
    re.compile(r"Class\.forName\("),
)
ASSERTION_PATTERN = re.compile(
    r"\b(assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|"
    r"assertSame|assertNotSame|assertThrows|assertArrayEquals|assertNotEquals|"
    r"expectContains|expectClean|expectNoIssues|check\(|require\(|"
    r"onNode\(|performClick\(|performTextInput\(|performScrollTo\(|"
    r"createAndroidComposeRule|ActivityScenario|UiDevice)\b"
)
EMPTY_TEST_PATTERN = re.compile(
    r"@Test\s+fun\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*\{\s*"
    r"(?://[^\n]*\n\s*)*\}",
    re.MULTILINE,
)


def walk_kotlin(root: Path) -> list[Path]:
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def is_unchanged_v210_source_contract(path: Path, repo_root: Path, text: str) -> bool:
    """Keep the v2.1.0 source-contract debt visible without allowing new debt."""

    if "sourceFile(" not in text or not (repo_root / ".git").exists():
        return False
    try:
        relative = path.relative_to(repo_root)
    except ValueError:
        return False
    result = subprocess.run(
        ["git", "diff", "--quiet", "v2.1.0", "--", str(relative)],
        cwd=repo_root,
        check=False,
    )
    return result.returncode == 0


def violations_for_directory(root: Path, repo_root: Path) -> list[str]:
    failures: list[str] = []
    ui_flow_names = {
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
        rel = path.relative_to(repo_root)
        name = path.name
        if name.startswith("Example"):
            failures.append(f"placeholder test is not allowed: {rel}")
        text = path.read_text(encoding="utf-8")
        if not is_unchanged_v210_source_contract(path, repo_root, text):
            for pattern in FORBIDDEN_PATTERNS:
                if pattern.search(text):
                    failures.append(f"forbidden test runtime/network pattern: {rel}")
        if EMPTY_TEST_PATTERN.search(text):
            failures.append(f"empty @Test body is not allowed: {rel}")
        if "@Test" in text and not ASSERTION_PATTERN.search(text):
            failures.append(f"test file has @Test but no assertion/UI operation: {rel}")
        if name in ui_flow_names and root.name == "androidTest":
            if not re.search(
                r"onNode\(|performClick\(|performTextInput\(|"
                r"createAndroidComposeRule|ActivityScenario|UiDevice",
                text,
            ):
                failures.append(f"UI flow test has no real Activity/Compose operation: {rel}")
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)
    root = args.root.resolve()
    failures = violations_for_directory(root / "app/src/test", root)
    failures.extend(violations_for_directory(root / "app/src/androidTest", root))
    if failures:
        print("\n".join(sorted(set(failures))), file=sys.stderr)
        return 1
    print("Test quality policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
