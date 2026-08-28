from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CiQualityPolicyTest(unittest.TestCase):
    def test_ci_restores_required_checks_and_static_policies(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for job in (
            "quality:",
            "build:",
            "coverage:",
            "visual-regression:",
            "managed-device-api26:",
            "managed-device-api35:",
            "performance:",
        ):
            self.assertIn(job, ci)
        for script in (
            "verify-sensitive-output-policy.py",
            "verify-compose-lifecycle-policy.py",
            "verify-ui-file-boundaries.py",
            "verify-test-quality-policy.py",
            "verify-localization-resources.py",
            "verify-localization-sources.py",
        ):
            self.assertIn(script, ci)

    def test_ci_builds_all_variants_and_smoke_tools_are_present(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for variant in (
            ":app:assembleGkdDebug",
            ":app:assemblePlayDebug",
            ":app:assembleGkdRelease",
            ":app:assemblePlayRelease",
        ):
            self.assertIn(variant, ci)
        self.assertIn("scripts/run-release-apk-smoke-emulator.sh", ci)
        self.assertIn("smoke-test-release-apk.sh", (ROOT / "scripts/run-release-apk-smoke-emulator.sh").read_text())

    def test_ruleset_and_environment_tools_have_safe_modes(self):
        ruleset = (ROOT / "scripts/apply-main-ruleset.sh").read_text(encoding="utf-8")
        self.assertIn("--dry-run", ruleset)
        self.assertIn("--check", ruleset)
        self.assertIn("--apply", ruleset)
        self.assertNotIn("GITHUB_TOKEN", ruleset)
        self.assertIn("command -v python3", ruleset)
        environment = (ROOT / "scripts/check-dev-environment.sh").read_text(encoding="utf-8")
        self.assertIn("--ci", environment)
        self.assertIn("JDK 21", environment)


if __name__ == "__main__":
    unittest.main()
