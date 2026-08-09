#!/usr/bin/env python3
"""Enforce the production file and Compose boundaries for Task 12.

This check deliberately scans every Kotlin source file in the migrated page and
overlay directories.  A filename such as ``Sections2.kt`` is just as much a
production boundary as ``Screen.kt``; excluding it would allow an oversized
implementation to be hidden behind a suffix.  Composable sizes are measured by
matching the declaration's braces rather than by taking a fixed window of lines.
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
OVERLAY_MODULES = {"usageguardrequest", "usageguardcountdown"}
PAGE_FILES = {
    "Route.kt",
    "Screen.kt",
    "UiState.kt",
    "Presenter.kt",
    "Sections.kt",
    "Dialogs.kt",
    "Editor.kt",
}
OVERLAY_FILES = {
    "ServiceHost.kt",
    "WindowController.kt",
    "Screen.kt",
    "UiState.kt",
    "Presenter.kt",
}
MAX_LINES = 500
MAX_COMPOSABLE_LINES = 180


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def _code_only(text: str) -> str:
    """Replace comments and literals with spaces while preserving newlines.

    Kotlin declarations and brace matching can then be inspected without braces
    inside strings or comments being mistaken for syntax.  Character literals,
    regular strings, triple-quoted strings, line comments and nested block
    comments are handled; all non-code characters are replaced by spaces.
    """

    out = list(text)
    i = 0
    n = len(text)
    block_depth = 0
    while i < n:
        if block_depth:
            if text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                block_depth += 1
                i += 2
            elif text.startswith("*/", i):
                out[i] = out[i + 1] = " "
                block_depth -= 1
                i += 2
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if text.startswith("//", i):
            out[i] = out[i + 1] = " "
            i += 2
            while i < n and text[i] != "\n":
                out[i] = " "
                i += 1
            continue
        if text.startswith("/*", i):
            out[i] = out[i + 1] = " "
            block_depth = 1
            i += 2
            continue
        if text.startswith('"""', i):
            out[i] = out[i + 1] = out[i + 2] = " "
            i += 3
            while i < n:
                if text.startswith('"""', i):
                    out[i] = out[i + 1] = out[i + 2] = " "
                    i += 3
                    break
                if text[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if text[i] in {'"', "'"}:
            quote = text[i]
            out[i] = " "
            i += 1
            escaped = False
            while i < n:
                ch = text[i]
                if ch == "\n" and quote == '"' and not escaped:
                    # Invalid ordinary string; leave the newline visible so
                    # line accounting remains deterministic.
                    break
                if ch == quote and not escaped:
                    out[i] = " "
                    i += 1
                    break
                if ch != "\n":
                    out[i] = " "
                escaped = ch == "\\" and not escaped
                if ch != "\\":
                    escaped = False
                i += 1
            continue
        i += 1
    return "".join(out)


def _line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def composable_spans(path: Path) -> list[tuple[int, int]]:
    """Return (start_line, end_line) for every @Composable function."""

    text = path.read_text(encoding="utf-8")
    code = _code_only(text)
    spans: list[tuple[int, int]] = []
    for annotation in re.finditer(r"@Composable\b", code):
        declaration = re.search(r"\bfun\b", code[annotation.end() :])
        if declaration is None:
            continue
        fun_start = annotation.end() + declaration.start()
        # Find the first body delimiter after the declaration.  A declaration
        # with an expression body has no brace and is bounded by its line.
        brace = code.find("{", fun_start)
        equals = code.find("=", fun_start)
        newline = code.find("\n", fun_start)
        if equals != -1 and (brace == -1 or equals < brace) and (
            newline == -1 or equals < newline
        ):
            spans.append((_line_number(text, annotation.start()), _line_number(text, equals)))
            continue
        if brace == -1:
            continue
        depth = 0
        end_offset = len(code) - 1
        for index in range(brace, len(code)):
            if code[index] == "{":
                depth += 1
            elif code[index] == "}":
                depth -= 1
                if depth == 0:
                    end_offset = index
                    break
        spans.append((_line_number(text, annotation.start()), _line_number(text, end_offset)))
    return spans


def verify_module(directory: Path, expected: set[str], label: str) -> list[str]:
    errors: list[str] = []
    if not directory.is_dir():
        return [f"missing {label} directory: {display_path(directory)}"]
    for name in sorted(expected):
        path = directory / name
        if not path.is_file():
            errors.append(f"missing {label} boundary: {display_path(path)}")

    for path in sorted(directory.rglob("*.kt")):
        count = line_count(path)
        if count > MAX_LINES:
            errors.append(f"{display_path(path)} has {count} lines (max {MAX_LINES})")
        if path.name == "ServiceHostLegacy.kt":
            errors.append(f"legacy overlay host is not allowed: {display_path(path)}")
        text = path.read_text(encoding="utf-8")
        if re.search(r"\bobject\s+\w*Boundary\s*\{\s*\}", _code_only(text), re.DOTALL):
            errors.append(f"empty boundary placeholder is not allowed: {display_path(path)}")
        for start, end in composable_spans(path):
            if end - start + 1 > MAX_COMPOSABLE_LINES:
                errors.append(
                    f"{display_path(path)} composable near line {start} spans {end - start + 1} "
                    f"lines (max {MAX_COMPOSABLE_LINES})"
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
            errors.append(f"{display_path(path)} has {line_count(path)} lines (max {MAX_LINES})")

    if errors:
        print("UI file boundary violations:")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("UI file boundaries: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
