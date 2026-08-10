#!/usr/bin/env python3
"""Verify generated performance reports against fixed thresholds."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--thresholds", required=True, type=Path)
    parser.add_argument("--root", default=ROOT)
    args = parser.parse_args(argv)
    thresholds = json.loads(args.thresholds.read_text())
    failures: list[str] = []
    required = [
        "coldStartupTimeToInitialDisplayMedianMs",
        "coldStartupTimeToInitialDisplayP95Ms",
        "warmStartupP95Ms",
        "frameOverrunP95Ms",
        "releaseApkBytesGrowthPercent",
        "releaseApkBytesAbsoluteGrowth",
    ]
    for key in required:
        if key not in thresholds:
            failures.append(f"missing threshold {key}")
    profile = Path(args.root) / "app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt"
    if not profile.is_file():
        failures.append("missing generated baseline profile")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Performance policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
