from pathlib import Path
import json
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-performance-reports.py"


def write_thresholds(root: Path, **overrides) -> Path:
    values = {
        "coldStartupTimeToInitialDisplayMedianMs": 1200,
        "coldStartupTimeToInitialDisplayP95Ms": 2000,
        "warmStartupP95Ms": 700,
        "frameOverrunP95Ms": 16,
        "releaseApkBytesGrowthPercent": 8,
        "releaseApkBytesAbsoluteGrowth": 2097152,
    }
    values.update(overrides)
    path = root / "thresholds.json"
    path.write_text(json.dumps(values), encoding="utf-8")
    return path


def write_profile(root: Path) -> Path:
    path = root / "app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt"
    path.parent.mkdir(parents=True)
    path.write_text("Landroidx/activity/ComponentActivity;\n", encoding="utf-8")
    return path


def write_benchmark(root: Path, cold_median=1000, cold_p95=1500, warm_p95=500,
                    frame_p95=10, cold_runs=10, warm_runs=10,
                    omit_startup_p95=False, cold_run_value=None) -> Path:
    base = root / "baselineprofile/build/outputs/managed_device_android_test_additional_output"
    base.mkdir(parents=True, exist_ok=True)
    path = base / "macro-benchmarkData.json"
    payload = {
        "benchmarks": [
            {
                "name": "startupCold",
                "metrics": {
                    "timeToInitialDisplayMs": {
                        "median": cold_median,
                        "runs": [cold_run_value if cold_run_value is not None else cold_median] * cold_runs,
                    } | ({} if omit_startup_p95 else {"P95": cold_p95}),
                },
                "sampledMetrics": {"frameOverrunMs": {"P95": frame_p95}},
            },
            {
                "name": "startupWarm",
                "metrics": {
                    "timeToInitialDisplayMs": {
                        "median": warm_p95,
                        "runs": [warm_p95] * warm_runs,
                    } | ({} if omit_startup_p95 else {"P95": warm_p95}),
                },
                "sampledMetrics": {"frameOverrunMs": {"P95": frame_p95}},
            },
        ]
    }
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def write_apk(path: Path, size: int, profiles=True) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("classes.dex", b"\0" * size)
        if profiles:
            zf.writestr("assets/dexopt/baseline.prof", b"profile")
            zf.writestr("assets/dexopt/baseline.profm", b"profile")
    return path


def write_compose_report(root: Path, unstable=0, stable_types=("UsageRequestUiState", "CapabilityNode")) -> Path:
    report = root / "compose-report/classes.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"unstable class Example$Count" for _ in range(unstable)]
    for stable_type in stable_types:
        lines.append(f"stable class li.songe.gkd.sdp.settings.{stable_type}")
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return report.parent


def write_compose_baseline(root: Path) -> Path:
    path = root / "compose-baseline.json"
    path.write_text(
        json.dumps(
            {
                "unstableClassCount": 0,
                "coreStableTypes": ["UsageRequestUiState", "CapabilityNode"],
            }
        ),
        encoding="utf-8",
    )
    return path


def run_verifier(root: Path, current_apk: Path, baseline_apk: Path,
                 compose_report: Path | None = None,
                 compose_baseline: Path | None = None,
                 threshold_overrides: dict | None = None) -> subprocess.CompletedProcess[str]:
    command = [
        "python3",
        str(VERIFIER),
        "--thresholds",
        str(write_thresholds(root, **(threshold_overrides or {}))),
        "--root",
        str(root),
        "--current-apk",
        str(current_apk),
        "--baseline-apk",
        str(baseline_apk),
    ]
    if compose_report is not None:
        command += ["--compose-report", str(compose_report)]
    if compose_baseline is not None:
        command += ["--compose-baseline", str(compose_baseline)]
    return subprocess.run(command, capture_output=True, text=True)


class PerformanceReportPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        write_profile(self.root)
        write_benchmark(self.root)
        self.current = write_apk(self.root / "current.apk", 1000)
        self.baseline = write_apk(self.root / "baseline.apk", 950)
        self.compose_report = write_compose_report(self.root)
        self.compose_baseline = write_compose_baseline(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def test_accepts_complete_fixture(self):
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Performance policy: OK", result.stdout)

    def test_rejects_cold_median_above_threshold(self):
        write_benchmark(self.root, cold_median=1300)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cold median 1300.00ms exceeds threshold 1200.00ms", result.stderr)

    def test_rejects_cold_p95_above_threshold(self):
        write_benchmark(self.root, cold_p95=2100)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cold P95 2100.00ms exceeds threshold 2000.00ms", result.stderr)

    def test_computes_startup_p95_from_runs_when_key_missing(self):
        write_benchmark(self.root, cold_p95=2100, omit_startup_p95=True, cold_run_value=2100)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cold P95 2100.00ms exceeds threshold 2000.00ms", result.stderr)

    def test_rejects_warm_p95_above_threshold(self):
        write_benchmark(self.root, warm_p95=800)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("warm P95 800.00ms exceeds threshold 700.00ms", result.stderr)

    def test_rejects_frame_p95_above_threshold(self):
        write_benchmark(self.root, frame_p95=20)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cold frameOverrun P95 20.00ms exceeds threshold 16.00ms", result.stderr)

    def test_rejects_too_few_samples(self):
        write_benchmark(self.root, cold_runs=9)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cold startup sample count is below 10", result.stderr)

    def test_rejects_non_finite_metric(self):
        write_benchmark(self.root, cold_median="not-a-number")
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing or non-finite cold median", result.stderr)

    def test_rejects_apk_relative_growth(self):
        current = write_apk(self.root / "current-large.apk", 10000)
        baseline = write_apk(self.root / "baseline-small.apk", 9000)
        result = run_verifier(
            self.root,
            current,
            baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exceeds threshold 8.00%", result.stderr)

    def test_rejects_apk_absolute_growth(self):
        current = write_apk(self.root / "current-large.apk", 10000)
        baseline = write_apk(self.root / "baseline-small.apk", 1000)
        result = run_verifier(
            self.root,
            current,
            baseline,
            self.compose_report,
            self.compose_baseline,
            threshold_overrides={"releaseApkBytesAbsoluteGrowth": 500},
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exceeds threshold 500.0 bytes", result.stderr)

    def test_rejects_missing_profile_assets(self):
        current = write_apk(self.root / "current-noprofile.apk", 1000, profiles=False)
        result = run_verifier(
            self.root,
            current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing assets/dexopt/baseline.prof", result.stderr)
        self.assertIn("missing assets/dexopt/baseline.profm", result.stderr)

    def test_rejects_unstable_class_growth(self):
        write_compose_report(self.root, unstable=1)
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Compose unstable class count 1 exceeds baseline 0", result.stderr)

    def test_rejects_missing_core_stable_type(self):
        write_compose_report(self.root, stable_types=("UsageRequestUiState",))
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("core stable type is not stable in report: CapabilityNode", result.stderr)

    def test_rejects_missing_compose_report(self):
        missing = self.root / "missing-compose-report"
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            missing,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Compose compiler report not found", result.stderr)

    def test_rejects_ambiguous_benchmark_data(self):
        write_benchmark(self.root / "app/build/outputs/managed_device_android_test_additional_output")
        result = run_verifier(
            self.root,
            self.current,
            self.baseline,
            self.compose_report,
            self.compose_baseline,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ambiguous macrobenchmark data", result.stderr)


if __name__ == "__main__":
    unittest.main()
