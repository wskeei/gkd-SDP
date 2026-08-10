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

    def test_ruleset_script_is_safe_and_has_required_modes(self):
        script = (ROOT / "scripts/apply-main-ruleset.sh").read_text()
        self.assertIn("--dry-run", script)
        self.assertIn("--check", script)
        self.assertIn("--apply", script)
        self.assertNotIn("GITHUB_TOKEN", script)
        self.assertNotIn("Authorization", script)


if __name__ == "__main__":
    unittest.main()
