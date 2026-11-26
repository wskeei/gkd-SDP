from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CiQualityPolicyTest(unittest.TestCase):
    def test_ci_keeps_lightweight_required_checks(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for job in (
            "quality:",
            "build:",
            "dependency-review:",
        ):
            self.assertIn(job, ci)
        for removed_job in (
            "coverage:",
            "visual-regression:",
            "managed-device-api26:",
            "managed-device-api35:",
            "performance:",
        ):
            self.assertNotIn(removed_job, ci)

    def test_ci_builds_all_variants_and_smoke_tests_minified_releases(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for variant in (
            ":app:assembleGkdDebug",
            ":app:assemblePlayDebug",
            ":app:assembleGkdRelease",
            ":app:assemblePlayRelease",
        ):
            self.assertIn(variant, ci)
        self.assertIn("scripts/run-release-apk-smoke-emulator.sh", ci)
        self.assertIn(
            "smoke-test-release-apk.sh",
            (ROOT / "scripts/run-release-apk-smoke-emulator.sh").read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
