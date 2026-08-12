#!/usr/bin/env python3
"""Verify generated performance reports against fixed thresholds."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]

REQUIRED_THRESHOLDS = [
    "coldStartupTimeToInitialDisplayMedianMs",
    "coldStartupTimeToInitialDisplayP95Ms",
    "warmStartupP95Ms",
    "frameOverrunP95Ms",
    "releaseApkBytesGrowthPercent",
    "releaseApkBytesAbsoluteGrowth",
]


def is_finite_number(value: object) -> bool:
    return isinstance(value, (int, float)) and isinstance(value, bool) is False and math.isfinite(float(value))


def find_benchmark_data(root: Path, failures: list[str]) -> list[Path]:
    results: list[Path] = []
    for base in (
        root / "baselineprofile/build/outputs/managed_device_android_test_additional_output",
        root / "app/build/outputs/managed_device_android_test_additional_output",
    ):
        if base.is_dir():
            results.extend(base.rglob("*-benchmarkData.json"))
    if not results:
        failures.append("missing macrobenchmark benchmarkData.json")
    elif len(results) > 1:
        failures.append(
            "ambiguous macrobenchmark data; expected exactly one benchmarkData.json, found: "
            + ", ".join(str(path) for path in results)
        )
    return results


def metric_value(benchmark: dict, metric: str, stat: str) -> object | None:
    for holder in (
        benchmark.get("metrics", {}).get(metric, {}),
        benchmark.get("sampledMetrics", {}).get(metric, {}),
    ):
        if stat in holder:
            return holder[stat]
        runs = holder.get("runs")
        if stat == "P95" and isinstance(runs, list) and runs:
            flattened = [value for run in runs if isinstance(run, list) for value in run]
            if not flattened:
                flattened = [value for value in runs if isinstance(value, (int, float))]
            if flattened and all(
                isinstance(value, (int, float)) and not isinstance(value, bool)
                for value in flattened
            ):
                ordered = sorted(float(value) for value in flattened)
                index = max(0, math.ceil(len(ordered) * 0.95) - 1)
                return ordered[min(index, len(ordered) - 1)]
        if stat == "median" and isinstance(runs, list) and runs:
            flattened = [value for run in runs if isinstance(run, list) for value in run]
            if not flattened:
                flattened = [value for value in runs if isinstance(value, (int, float))]
            if flattened:
                ordered = sorted(float(value) for value in flattened)
                middle = len(ordered) // 2
                if len(ordered) % 2 == 1:
                    return ordered[middle]
                return (ordered[middle - 1] + ordered[middle]) / 2.0
    return None


def run_count(benchmark: dict, metric: str) -> int:
    for holder in (
        benchmark.get("metrics", {}).get(metric, {}),
        benchmark.get("sampledMetrics", {}).get(metric, {}),
    ):
        runs = holder.get("runs")
        if isinstance(runs, list):
            return len(runs)
    return 0


def check_benchmark_data(path: Path, thresholds: dict, failures: list[str]) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    benchmarks = {item["name"]: item for item in data.get("benchmarks", [])}
    cold = benchmarks.get("startupCold")
    warm = benchmarks.get("startupWarm")
    if cold is None or warm is None:
        failures.append(f"missing startupCold/startupWarm benchmarks in {path}")
        return

    cold_median = metric_value(cold, "timeToInitialDisplayMs", "median")
    cold_p95 = metric_value(cold, "timeToInitialDisplayMs", "P95")
    warm_p95 = metric_value(warm, "timeToInitialDisplayMs", "P95")
    cold_frame = metric_value(cold, "frameOverrunMs", "P95")
    warm_frame = metric_value(warm, "frameOverrunMs", "P95")

    for label, value in (
        ("cold median", cold_median),
        ("cold P95", cold_p95),
        ("warm P95", warm_p95),
        ("cold frameOverrun P95", cold_frame),
        ("warm frameOverrun P95", warm_frame),
    ):
        if not is_finite_number(value):
            failures.append(f"missing or non-finite {label} in {path}")
            return

    if run_count(cold, "timeToInitialDisplayMs") < 10:
        failures.append(f"cold startup sample count is below 10 in {path}")
    if run_count(warm, "timeToInitialDisplayMs") < 10:
        failures.append(f"warm startup sample count is below 10 in {path}")

    comparisons = (
        ("cold median", float(cold_median), thresholds["coldStartupTimeToInitialDisplayMedianMs"], path),
        ("cold P95", float(cold_p95), thresholds["coldStartupTimeToInitialDisplayP95Ms"], path),
        ("warm P95", float(warm_p95), thresholds["warmStartupP95Ms"], path),
        ("cold frameOverrun P95", float(cold_frame), thresholds["frameOverrunP95Ms"], path),
        ("warm frameOverrun P95", float(warm_frame), thresholds["frameOverrunP95Ms"], path),
    )
    for label, actual, limit, source in comparisons:
        if actual > float(limit):
            failures.append(
                f"{label} {actual:.2f}ms exceeds threshold {float(limit):.2f}ms in {source}"
            )


def check_apk_policy(current_apk: Path, baseline_apk: Path, thresholds: dict, failures: list[str]) -> None:
    if not current_apk.is_file():
        failures.append(f"current APK not found: {current_apk}")
        return
    if not baseline_apk.is_file():
        failures.append(f"baseline APK not found: {baseline_apk}")
        return

    current_size = current_apk.stat().st_size
    baseline_size = baseline_apk.stat().st_size
    if baseline_size <= 0:
        failures.append(f"baseline APK has invalid size: {baseline_apk}")
        return
    growth = (current_size - baseline_size) / baseline_size * 100.0
    if growth > float(thresholds["releaseApkBytesGrowthPercent"]):
        failures.append(
            f"release APK growth {growth:.2f}% exceeds threshold "
            f"{float(thresholds['releaseApkBytesGrowthPercent']):.2f}%"
        )
    absolute_growth = current_size - baseline_size
    if absolute_growth > float(thresholds["releaseApkBytesAbsoluteGrowth"]):
        failures.append(
            f"release APK absolute growth {absolute_growth} bytes exceeds threshold "
            f"{float(thresholds['releaseApkBytesAbsoluteGrowth'])} bytes"
        )

    with zipfile.ZipFile(current_apk) as zf:
        for profile_name in ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"):
            try:
                info = zf.getinfo(profile_name)
            except KeyError:
                failures.append(f"current APK is missing {profile_name}")
                continue
            if info.file_size <= 0:
                failures.append(f"current APK has empty {profile_name}")


def parse_compose_report(report: Path) -> dict[str, object]:
    candidates: list[Path] = []
    if report.is_file():
        candidates.append(report)
    elif report.is_dir():
        candidates.extend(report.glob("*.txt"))
        candidates.extend(report.glob("*.json"))
    if not candidates:
        raise FileNotFoundError(f"Compose compiler report not found: {report}")

    class_lines: list[str] = []
    json_payloads: list[dict[str, object]] = []
    for candidate in candidates:
        if candidate.suffix == ".json":
            payload = json.loads(candidate.read_text(encoding="utf-8"))
            json_payloads.append(payload)
            continue
        if candidate.name == "classes.txt" or candidate.name.endswith("-classes.txt"):
            class_lines.extend(candidate.read_text(encoding="utf-8").splitlines())

    unstable = 0
    stable_classes: set[str] = set()
    for line in class_lines:
        stripped = line.strip()
        if stripped.startswith("unstable class "):
            unstable += 1
        elif stripped.startswith("stable class "):
            stable_name = stripped.removeprefix("stable class ").strip()
            if stable_name.endswith(" {"):
                stable_name = stable_name[:-2].strip()
            stable_classes.add(stable_name)
        elif stripped.startswith("unstable class") or stripped.startswith("stable class"):
            pass

    for payload in json_payloads:
        if isinstance(payload.get("unstableClassCount"), int):
            unstable = max(unstable, int(payload["unstableClassCount"]))
        for kind in ("stableClasses", "knownStableClasses"):
            values = payload.get(kind)
            if isinstance(values, list):
                stable_classes.update(str(value) for value in values)

    if not class_lines and not json_payloads:
        raise ValueError(f"Compose compiler report is empty: {report}")
    return {"unstableClassCount": unstable, "stableClasses": stable_classes}


def check_compose_report(
    report: Path,
    baseline: Path,
    failures: list[str],
) -> None:
    if not baseline.is_file():
        failures.append(f"Compose stability baseline not found: {baseline}")
        return
    try:
        report_data = parse_compose_report(report)
        baseline_data = json.loads(baseline.read_text(encoding="utf-8"))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        failures.append(f"invalid Compose stability input: {exc}")
        return

    unstable_limit = baseline_data.get("unstableClassCount")
    if not isinstance(unstable_limit, int):
        failures.append("Compose stability baseline missing integer unstableClassCount")
        return
    unstable_count = int(report_data["unstableClassCount"])
    if unstable_count > unstable_limit:
        failures.append(
            f"Compose unstable class count {unstable_count} exceeds baseline {unstable_limit}"
        )

    stable_classes = {str(value) for value in report_data["stableClasses"]}
    core_types = baseline_data.get("coreStableTypes", [])
    if not isinstance(core_types, list) or not core_types:
        failures.append("Compose stability baseline missing coreStableTypes")
        return
    for core_type in core_types:
        if not any(
            stable_name == core_type or stable_name.endswith("." + core_type)
            for stable_name in stable_classes
        ):
            failures.append(f"core stable type is not stable in report: {core_type}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--thresholds", required=True, type=Path)
    parser.add_argument("--root", default=ROOT, type=Path)
    parser.add_argument("--current-apk", required=True, type=Path)
    parser.add_argument("--baseline-apk", required=True, type=Path)
    parser.add_argument("--compose-report", required=True, type=Path)
    parser.add_argument("--compose-baseline", required=True, type=Path)
    args = parser.parse_args(argv)

    failures: list[str] = []
    try:
        thresholds = json.loads(args.thresholds.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        failures.append(f"invalid performance thresholds: {exc}")
        return _finish(failures)

    for key in REQUIRED_THRESHOLDS:
        value = thresholds.get(key)
        if not is_finite_number(value):
            failures.append(f"missing or non-finite threshold {key}")
            return _finish(failures)

    profile = args.root / "app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt"
    if not profile.is_file():
        failures.append("missing generated baseline profile")
    benchmark_data = find_benchmark_data(args.root, failures)
    for path in benchmark_data:
        check_benchmark_data(path, thresholds, failures)

    check_apk_policy(args.current_apk, args.baseline_apk, thresholds, failures)
    compose_report = args.compose_report
    if not compose_report.exists():
        failures.append(f"Compose compiler report not found: {compose_report}")
        return _finish(failures)
    check_compose_report(compose_report, args.compose_baseline, failures)

    return _finish(failures)


def _finish(failures: list[str]) -> int:
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Performance policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
