#!/usr/bin/env python3
"""Enforce the file boundaries used by Compose pages and overlay hosts.

The legacy section files are intentionally not treated as hosts: they are the
implementation units being migrated behind the small Route/Screen boundary.
New hosts must stay small so state, window ownership and rendering do not grow
back into one lifecycle-sensitive file.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/kotlin/li/songe/gkd/sdp/ui"
SERVICE_ROOT = ROOT / "app/src/main/kotlin/li/songe/gkd/sdp/service"
PAGE_MODULES = {
    "appblocker",
    "focuslock",
    "focusmode",
    "urlblocker",
    "usageguard",
    "usagereview",
    "actionlog",
    "settings",
    "advanced",
    "imagepreview",
}
PAGE_FILES = {
    "Route.kt",
    "Screen.kt",
    "UiState.kt",
    "Presenter.kt",
    "Sections.kt",
    "Dialogs.kt",
    "Editor.kt",
}
OVERLAY_MODULES = {"usageguardrequest", "usageguardcountdown"}
OVERLAY_FILES = {
    "ServiceHost.kt",
    "WindowController.kt",
    "Screen.kt",
    "UiState.kt",
    "Presenter.kt",
    "ServiceHostLegacy.kt",
}
MAX_LINES = 500
MAX_COMPOSABLE_LINES = 180


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def composable_spans(path: Path) -> list[tuple[int, int]]:
    text = path.read_text(encoding="utf-8")
    starts = [m.start() for m in re.finditer(r"@Composable\b", text)]
    lines = text.splitlines()
    offsets: list[int] = []
    total = 0
    for line in lines:
        offsets.append(total)
        total += len(line) + 1
    spans: list[tuple[int, int]] = []
    for start in starts:
        line = next((i for i, offset in enumerate(offsets) if offset > start), len(lines))
        line = max(0, line - 1)
        # The host files contain only wrappers; the next declaration/end is enough
        # for a conservative guard without parsing Kotlin.
        end = min(len(lines), line + MAX_COMPOSABLE_LINES + 1)
        spans.append((line + 1, end))
    return spans


def verify_module(directory: Path, expected: set[str], label: str) -> list[str]:
    errors: list[str] = []
    if not directory.is_dir():
        return [f"missing {label} directory: {directory.relative_to(ROOT)}"]
    for name in sorted(expected):
        path = directory / name
        if not path.is_file():
            errors.append(f"missing {label} boundary: {path.relative_to(ROOT)}")
    for path in directory.iterdir():
        if path.suffix == ".kt" and path.name in {"Screen.kt", "ServiceHost.kt"}:
            count = line_count(path)
            if count > MAX_LINES:
                errors.append(f"{path.relative_to(ROOT)} has {count} lines (max {MAX_LINES})")
            for start, end in composable_spans(path):
                if end - start > MAX_COMPOSABLE_LINES:
                    errors.append(
                        f"{path.relative_to(ROOT)} composable near line {start} exceeds "
                        f"{MAX_COMPOSABLE_LINES} lines"
                    )
    return errors


def main() -> int:
    errors: list[str] = []
    for module in sorted(PAGE_MODULES):
        errors.extend(verify_module(UI_ROOT / module, PAGE_FILES, f"page/{module}"))
    for module in sorted(OVERLAY_MODULES):
        errors.extend(verify_module(SERVICE_ROOT / module, OVERLAY_FILES, f"overlay/{module}"))

    # No oversized page/overlay host may remain at the old flat locations.
    flat_hosts = [
        *UI_ROOT.glob("*Page.kt"),
        UI_ROOT / "home/SettingsPage.kt",
        *SERVICE_ROOT.glob("*OverlayService.kt"),
    ]
    for path in flat_hosts:
        if path.is_file() and line_count(path) > MAX_LINES:
            errors.append(f"{path.relative_to(ROOT)} has {line_count(path)} lines (max {MAX_LINES})")

    if errors:
        print("UI file boundary violations:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("UI file boundaries: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
