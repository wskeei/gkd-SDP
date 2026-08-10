#!/usr/bin/env python3
"""Enforce lifecycle-aware Flow collection in production Compose code."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "app" / "src" / "main" / "kotlin"


def main() -> int:
    failures: list[str] = []
    gradle = (ROOT / "gradle" / "libs.versions.toml").read_text()
    if "androidx-lifecycle-runtime-compose" not in gradle:
        failures.append("lifecycle-runtime-compose dependency is missing")

    helper = KOTLIN_ROOT / "li" / "songe" / "gkd" / "sdp" / "ui" / "share" / "ServiceOverlayLifecycleOwner.kt"
    if not helper.is_file():
        failures.append("ServiceOverlayLifecycleOwner.kt is missing")

    legacy_import = "import androidx.compose.runtime.collectAsState"
    legacy_call = re.compile(r"(?<!WithLifecycle)\bcollectAsState\(")
    for source in KOTLIN_ROOT.rglob("*.kt"):
        text = source.read_text()
        if legacy_import in text:
            failures.append(f"legacy collectAsState import: {source.relative_to(ROOT)}")
        if legacy_call.search(text):
            failures.append(f"legacy collectAsState call: {source.relative_to(ROOT)}")
        if "collectAsStateWithLifecycle(" in text and "import androidx.lifecycle.compose.collectAsStateWithLifecycle" not in text:
            # Wildcard runtime imports do not expose lifecycle-compose extensions.
            failures.append(f"missing lifecycle-compose import: {source.relative_to(ROOT)}")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Compose lifecycle policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
