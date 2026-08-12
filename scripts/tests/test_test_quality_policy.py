from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class TestQualityPolicyTest(unittest.TestCase):
    def run_policy(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(ROOT / "scripts" / "verify-test-quality-policy.py"), "--root", str(root)],
            capture_output=True,
            text=True,
        )

    def test_rejects_source_string_contracts_and_placeholders(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            unit = root / "app/src/test/kotlin"
            unit.mkdir(parents=True)
            (unit / "ExampleUnitTest.kt").write_text("class ExampleUnitTest {}")
            (unit / "UsageGuardRequestLayoutContractTest.kt").write_text(
                "class X { @Test fun test() { sourceFile(\"app/src/main/kotlin/x.kt\").readText() } }"
            )

            result = self.run_policy(root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("placeholder test", result.stderr)
            self.assertIn("forbidden test runtime/network pattern", result.stderr)

    def test_accepts_behavioral_tests(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            unit = root / "app/src/test/kotlin"
            android = root / "app/src/androidTest/kotlin"
            unit.mkdir(parents=True)
            android.mkdir(parents=True)
            (unit / "BehaviorTest.kt").write_text(
                "class BehaviorTest { fun test() { check(true) } }"
            )
            (android / "BehaviorInstrumentedTest.kt").write_text(
                "class BehaviorInstrumentedTest { fun test() { check(true) } }"
            )

            result = self.run_policy(root)

            self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_empty_test_and_missing_assertion(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            unit = root / "app/src/test/kotlin"
            unit.mkdir(parents=True)
            (unit / "EmptyTest.kt").write_text(
                "class EmptyTest { @Test fun empty() {} }"
            )
            (unit / "NoAssertionTest.kt").write_text(
                "class NoAssertionTest { @Test fun noAssertion() { println(\"x\") } }"
            )

            result = self.run_policy(root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("empty @Test body", result.stderr)
            self.assertIn("no assertion/UI operation", result.stderr)

    def test_rejects_ui_flow_without_compose_activity_operation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android = root / "app/src/androidTest/kotlin"
            android.mkdir(parents=True)
            (android / "AppNavigationTest.kt").write_text(
                "class AppNavigationTest { @Test fun nav() { assertEquals(1, 1) } }"
            )

            result = self.run_policy(root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("UI flow test has no real Activity/Compose operation", result.stderr)


if __name__ == "__main__":
    unittest.main()
