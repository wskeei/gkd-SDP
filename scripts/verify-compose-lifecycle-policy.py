#!/usr/bin/env python3
"""Guard Compose lifecycle usage while retaining the v2.1.0 API surface.

v2.1.0 predates lifecycle-runtime-compose and uses collectAsState throughout
the existing flat UI. This check prevents new direct collection calls in
changes after that baseline and verifies the countdown overlay owns its
temporary coroutine through the Service lifecycle.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys


def changed_line_numbers(root: Path, baseline_ref: str) -> dict[str, set[int]] | None:
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
    changed: dict[str, set[int]] = {}
    current_path: str | None = None
    new_line = 0
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current_path = line[6:]
            changed.setdefault(current_path, set())
        elif line.startswith("@@"):
            match = re.search(r"\+(\d+)", line)
            if match:
                new_line = int(match.group(1))
        elif current_path is not None and line.startswith("+"):
            changed[current_path].add(new_line)
            new_line += 1
        elif current_path is not None and not line.startswith("-"):
            new_line += 1
    return changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline-ref", default="v2.1.0")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    failures: list[str] = []
    changed = changed_line_numbers(root, args.baseline_ref)
    direct_collection = re.compile(r"\bcollectAsState\s*\(")
    kotlin_root = root / "app/src/main/kotlin"
    for path in sorted(kotlin_root.rglob("*.kt")):
        relative = str(path.relative_to(root))
        text = path.read_text(encoding="utf-8")
        allowed_lines = None if changed is None else changed.get(relative, set())
        if changed is not None and not allowed_lines:
            continue
        for match in direct_collection.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            if allowed_lines is None or line in allowed_lines:
                failures.append(f"new direct collectAsState call: {relative}:{line}")
        if "collectAsStateWithLifecycle(" in text and "import androidx.lifecycle.compose.collectAsStateWithLifecycle" not in text:
            failures.append(f"missing lifecycle-compose import: {relative}")

    overlay = root / "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt"
    if not overlay.is_file():
        failures.append("UsageGuardCountdownOverlayService.kt is missing")
    else:
        overlay_text = overlay.read_text(encoding="utf-8")
        for required in (
            "setViewTreeLifecycleOwner",
            "lifecycleScope",
            "restoreOverlayJob?.cancel()",
            "override fun onDestroy()",
        ):
            if required not in overlay_text:
                failures.append(f"countdown overlay lifecycle contract is missing: {required}")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Compose lifecycle policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
