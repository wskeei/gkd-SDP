from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CiQualityPolicyTest(unittest.TestCase):
    def test_ci_builds_all_four_variants(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        for variant in (":app:assembleGkdDebug", ":app:assemblePlayDebug", ":app:assembleGkdRelease", ":app:assemblePlayRelease"):
            self.assertIn(variant, ci)

    def test_ci_runs_test_quality_and_coverage(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("verify-test-quality-policy.py", ci)
        self.assertIn("koverVerifyGkdDebug", ci)

    def test_ci_runs_managed_device_and_performance_gates(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("managed-device-api26:", ci)
        self.assertIn("managed-device-api35:", ci)
        self.assertIn("performance:", ci)
        self.assertIn(":app:pixel2Api26GkdDebugAndroidTest", ci)
        self.assertIn(":app:pixel6Api35GkdDebugAndroidTest", ci)
        self.assertIn(":app:pixel6Api35PlayDebugAndroidTest", ci)
        self.assertIn(":app:generateGkdReleaseBaselineProfile", ci)
        self.assertIn(":baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest", ci)
        self.assertIn("verify-performance-reports.py", ci)

    def test_ci_starts_the_actual_minified_release_on_api_boundaries(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        nightly = (ROOT / ".github/workflows/nightly.yml").read_text()

        smoke_runner = (ROOT / "scripts/run-release-apk-smoke-emulator.sh").read_text()
        self.assertIn("smoke-test-release-apk.sh", smoke_runner)
        self.assertNotIn("rg ", smoke_runner)
        for task in (
            "scripts/run-release-apk-smoke-emulator.sh",
        ):
            self.assertIn(task, ci)
            self.assertIn(task, nightly)
        for workflow in (ci, nightly):
            self.assertIn("--api 26", workflow)
            self.assertIn("--api 35", workflow)
            self.assertIn("app-gkd-release.apk", workflow)
            self.assertIn("app-play-release.apk", workflow)

    def test_ruleset_script_is_safe_and_has_required_modes(self):
        script = (ROOT / "scripts/apply-main-ruleset.sh").read_text()
        self.assertIn("--dry-run", script)
        self.assertIn("--check", script)
        self.assertIn("--apply", script)
        self.assertNotIn("GITHUB_TOKEN", script)
        self.assertNotIn("Authorization", script)


if __name__ == "__main__":
    unittest.main()
