#!/usr/bin/env python3
"""Verify generated performance reports against fixed thresholds."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def percentile(values: list[float], percent: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, math.ceil(percent / 100 * len(ordered)) - 1)
    return ordered[index]


def find_benchmark_data(root: Path) -> list[Path]:
    results: list[Path] = []
    for base in (
        root / "baselineprofile/build/outputs/managed_device_android_test_additional_output",
        root / "app/build/outputs/managed_device_android_test_additional_output",
    ):
        if base.is_dir():
            results.extend(base.rglob("*-benchmarkData.json"))
    return results


def check_benchmark_data(path: Path, thresholds: dict[str, float], failures: list[str]) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    benchmarks = {item["name"]: item for item in data.get("benchmarks", [])}
    cold = benchmarks.get("startupCold")
    warm = benchmarks.get("startupWarm")
    if cold is None or warm is None:
        failures.append(f"missing startupCold/startupWarm benchmarks in {path}")
        return
    cold_timing = cold.get("metrics", {}).get("timeToInitialDisplayMs", {})
    cold_runs = cold_timing.get("runs", [])
    cold_median = cold_timing.get("median")
    if cold_median is None or len(cold_runs) < 10:
        failures.append(f"incomplete cold startup timing in {path}")
    else:
        if cold_median > thresholds["coldStartupTimeToInitialDisplayMedianMs"]:
            failures.append(f"cold startup median {cold_median:.1f}ms exceeds threshold")
        if percentile(cold_runs, 95) > thresholds["coldStartupTimeToInitialDisplayP95Ms"]:
            failures.append(
                f"cold startup P95 {percentile(cold_runs, 95):.1f}ms exceeds threshold"
            )
    warm_timing = warm.get("metrics", {}).get("timeToInitialDisplayMs", {})
    warm_runs = warm_timing.get("runs", [])
    if len(warm_runs) < 10:
        failures.append(f"incomplete warm startup timing in {path}")
    elif percentile(warm_runs, 95) > thresholds["warmStartupP95Ms"]:
        failures.append(
            f"warm startup P95 {percentile(warm_runs, 95):.1f}ms exceeds threshold"
        )
    if cold.get("sampledMetrics", {}).get("frameOverrunMs", {}).get("P95") is None:
        failures.append(f"missing cold frameOverrun P95 in {path}")
    if warm.get("sampledMetrics", {}).get("frameOverrunMs", {}).get("P95") is None:
        failures.append(f"missing warm frameOverrun P95 in {path}")


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
    thresholds_float = {key: float(thresholds[key]) for key in required}
    profile = Path(args.root) / "app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt"
    if not profile.is_file():
        failures.append("missing generated baseline profile")
    benchmark_data = find_benchmark_data(Path(args.root))
    if not benchmark_data:
        failures.append("missing macrobenchmark benchmarkData.json")
    for path in benchmark_data:
        check_benchmark_data(path, thresholds_float, failures)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Performance policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
