from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class PerformanceReportPolicyTest(unittest.TestCase):
    def test_rejects_missing_profile_or_thresholds(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            thresholds = root / "thresholds.json"
            thresholds.write_text(
                '{"coldStartupTimeToInitialDisplayMedianMs":1200,'
                '"coldStartupTimeToInitialDisplayP95Ms":2000,'
                '"warmStartupP95Ms":700,'
                '"frameOverrunP95Ms":16,'
                '"releaseApkBytesGrowthPercent":8,'
                '"releaseApkBytesAbsoluteGrowth":2097152}'
            )
            result = subprocess.run(
                [
                    "python3",
                    str(ROOT / "scripts/verify-performance-reports.py"),
                    "--thresholds",
                    str(thresholds),
                    "--root",
                    str(root),
                ],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing generated baseline profile", result.stderr)

    def test_rejects_missing_benchmark_metrics(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            profile = (
                root
                / "app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt"
            )
            profile.parent.mkdir(parents=True)
            profile.write_text("Landroidx/activity/ComponentActivity;\n", encoding="utf-8")
            thresholds = root / "thresholds.json"
            thresholds.write_text(
                '{"coldStartupTimeToInitialDisplayMedianMs":1200,'
                '"coldStartupTimeToInitialDisplayP95Ms":2000,'
                '"warmStartupP95Ms":700,'
                '"frameOverrunP95Ms":16,'
                '"releaseApkBytesGrowthPercent":8,'
                '"releaseApkBytesAbsoluteGrowth":2097152}'
            )
            result = subprocess.run(
                [
                    "python3",
                    str(ROOT / "scripts/verify-performance-reports.py"),
                    "--thresholds",
                    str(thresholds),
                    "--root",
                    str(root),
                ],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing macrobenchmark benchmarkData.json", result.stderr)


if __name__ == "__main__":
    unittest.main()
