import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"


class V210QualityGatesTest(unittest.TestCase):
    def test_ci_declares_all_main_protection_jobs(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        required_jobs = (
            "quality",
            "build",
            "coverage",
            "visual-regression",
            "managed-device-api26",
            "managed-device-api35",
            "performance",
            "dependency-review",
        )

        for job in required_jobs:
            with self.subTest(job=job):
                self.assertRegex(
                    workflow,
                    re.compile(rf"^  {re.escape(job)}:\s*$", re.MULTILINE),
                )

    def test_v210_quality_infrastructure_is_present(self):
        required_paths = (
            ROOT / "baselineprofile/build.gradle.kts",
            ROOT / "quality-lint/build.gradle.kts",
            ROOT / "config/quality/kover-includes.txt",
            ROOT / "config/quality/kover-excludes.txt",
            ROOT / "config/quality/performance-thresholds.json",
            ROOT / "config/quality/compose-stability-baseline.json",
            ROOT / "app/lint-baseline.xml",
            ROOT / "scripts/verify-kover-report.py",
            ROOT / "scripts/verify-performance-reports.py",
            ROOT / "scripts/run-release-apk-smoke-emulator.sh",
            ROOT / "scripts/smoke-test-release-apk.sh",
        )

        missing = [str(path.relative_to(ROOT)) for path in required_paths if not path.is_file()]
        self.assertEqual([], missing, "missing v2.1.0 quality infrastructure: " + ", ".join(missing))

    def test_app_build_registers_gate_capabilities(self):
        app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")

        for marker in (
            "libs.plugins.kotlinx.kover",
            "libs.plugins.compose.screenshot",
            "libs.plugins.baselineprofile",
            "testOptions",
            "kover {",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, app_build)

        self.assertIn("libs.plugins.kotlin.jvm", root_build)
        self.assertIn(":baselineprofile", settings)
        self.assertIn(":quality-lint", settings)
        self.assertIn('lintChecks(project(":quality-lint"))', app_build)
        self.assertIn('baseline = rootProject.file("app/lint-baseline.xml")', app_build)

    def test_ci_runs_each_gate_against_v210_compatible_inputs(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        required_commands = (
            ":app:koverVerifyGkdDebug",
            ":app:validateGkdDebugScreenshotTest",
            ":app:pixel2Api26GkdDebugAndroidTest",
            ":app:pixel6Api35GkdDebugAndroidTest",
            ":app:pixel6Api35PlayDebugAndroidTest",
            ":app:generateGkdReleaseBaselineProfile",
            ":baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest",
            "scripts/verify-kover-report.py",
            "scripts/verify-performance-reports.py",
            "scripts/run-release-apk-smoke-emulator.sh",
            "refs/tags/v2.1.0:refs/tags/v2.1.0",
        )
        for command in required_commands:
            with self.subTest(command=command):
                self.assertIn(command, workflow)

    def test_release_smoke_wrappers_are_connected(self):
        wrapper = (ROOT / "scripts/run-release-apk-smoke-emulator.sh").read_text(encoding="utf-8")
        smoke_test = (ROOT / "scripts/smoke-test-release-apk.sh").read_text(encoding="utf-8")
        self.assertIn('smoke-test-release-apk.sh', wrapper)
        self.assertIn('Status: ok', smoke_test)
        self.assertIn('launcher activity was not resumed', smoke_test)

    def test_release_smoke_cleanup_waits_for_emulator_before_removing_avd(self):
        wrapper = (ROOT / "scripts/run-release-apk-smoke-emulator.sh").read_text(encoding="utf-8")

        self.assertIn('"$adb_bin" -s "$serial" emu kill', wrapper)
        self.assertIn('timeout --kill-after=5s 10s "$adb_bin" -s "$serial" emu kill', wrapper)
        self.assertIn('wait "$emulator_pid"', wrapper)
        self.assertIn("for _ in {1..10}; do", wrapper)
        self.assertIn('rm -rf -- "$smoke_root"', wrapper)

    def test_visual_regression_has_v210_usage_overlay_previews(self):
        screenshot_files = list((ROOT / "app/src/screenshotTest").rglob("*.kt"))
        screenshot_source = "\n".join(path.read_text(encoding="utf-8") for path in screenshot_files)

        self.assertIn("UsageGuardCountdownOverlayContent", screenshot_source)
        self.assertIn("@PreviewTest", screenshot_source)


if __name__ == "__main__":
    unittest.main()
