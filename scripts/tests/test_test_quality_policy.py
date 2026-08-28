from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-test-quality-policy.py"


class TestQualityPolicyTest(unittest.TestCase):
    def run_policy(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(VERIFIER), "--root", str(root)],
            capture_output=True,
            text=True,
        )

    def test_rejects_placeholders_and_source_contracts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            unit = root / "app/src/test/kotlin"
            unit.mkdir(parents=True)
            (unit / "ExampleUnitTest.kt").write_text(
                "class ExampleUnitTest {}",
                encoding="utf-8",
            )
            (unit / "ContractTest.kt").write_text(
                'class ContractTest { @Test fun test() { sourceFile("app/src/main/kotlin/x.kt").readText() } }',
                encoding="utf-8",
            )

            result = self.run_policy(root)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("placeholder test", result.stderr)
        self.assertIn("forbidden test runtime/network pattern", result.stderr)

    def test_accepts_behavioral_tests(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            unit = root / "app/src/test/kotlin"
            android = root / "app/src/androidTest/kotlin"
            unit.mkdir(parents=True)
            android.mkdir(parents=True)
            (unit / "BehaviorTest.kt").write_text(
                "class BehaviorTest { @Test fun test() { check(true) } }",
                encoding="utf-8",
            )
            (android / "BehaviorInstrumentedTest.kt").write_text(
                "class BehaviorInstrumentedTest { @Test fun test() { check(true) } }",
                encoding="utf-8",
            )

            result = self.run_policy(root)

        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_empty_tests_and_missing_assertions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            unit = root / "app/src/test/kotlin"
            unit.mkdir(parents=True)
            (unit / "EmptyTest.kt").write_text(
                "class EmptyTest { @Test fun empty() {} }",
                encoding="utf-8",
            )
            (unit / "NoAssertionTest.kt").write_text(
                'class NoAssertionTest { @Test fun noAssertion() { println("x") } }',
                encoding="utf-8",
            )

            result = self.run_policy(root)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty @Test body", result.stderr)
        self.assertIn("no assertion/UI operation", result.stderr)

    def test_requires_real_operation_for_named_ui_flow_tests(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            android = root / "app/src/androidTest/kotlin"
            android.mkdir(parents=True)
            (android / "AppNavigationTest.kt").write_text(
                "class AppNavigationTest { @Test fun nav() { assertEquals(1, 1) } }",
                encoding="utf-8",
            )

            result = self.run_policy(root)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("UI flow test has no real Activity/Compose operation", result.stderr)


if __name__ == "__main__":
    unittest.main()
