#!/usr/bin/env python3
"""Enforce size boundaries for the v2.1.0 flat UI and Service layout.

The later modular page layout is intentionally not restored on this branch.
Existing v2.1.0 files therefore have explicit checked-in budgets, while every
new UI or overlay file uses the stricter 500-line default. This keeps the
rollback compatible and prevents new monoliths from being added.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/kotlin/li/songe/gkd/sdp/ui"
SERVICE_ROOT = ROOT / "app/src/main/kotlin/li/songe/gkd/sdp/service"
DEFAULT_MAX_LINES = 500
LEGACY_LINE_BUDGETS = {
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt": 1503,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt": 1449,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockerComponents.kt": 1383,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusModePage.kt": 1203,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt": 852,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt": 761,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SettingsPage.kt": 750,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/TriStateSwitch.kt": 695,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockVm.kt": 653,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/AdvancedPage.kt": 644,
    "app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardCoordinator.kt": 640,
    "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt": 624,
    "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt": 650,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/ImagePreviewPage.kt": 544,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt": 533,
    "app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt": 521,
}


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def verify(root: Path) -> list[str]:
    failures: list[str] = []
    for source_root in (root / "app/src/main/kotlin/li/songe/gkd/sdp/ui", root / "app/src/main/kotlin/li/songe/gkd/sdp/service"):
        if not source_root.is_dir():
            failures.append(f"missing UI source root: {source_root}")
            continue
        for path in sorted(source_root.rglob("*.kt")):
            relative = str(path.relative_to(root))
            count = line_count(path)
            budget = LEGACY_LINE_BUDGETS.get(relative, DEFAULT_MAX_LINES)
            if count > budget:
                failures.append(f"{relative} has {count} lines (max {budget})")
            if path.name == "ServiceHostLegacy.kt":
                failures.append(f"legacy overlay host is not allowed: {relative}")
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    failures = verify(args.root.resolve())
    if failures:
        print("UI file boundary violations:", file=sys.stderr)
        print("\n".join(f"- {failure}" for failure in failures), file=sys.stderr)
        return 1
    print("UI file boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
