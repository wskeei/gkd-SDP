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
                "class X { fun test() { sourceFile(\"x\").readText() } }"
            )

            result = self.run_policy(root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("placeholder test", result.stderr)
            self.assertIn("source-string contract", result.stderr)

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


if __name__ == "__main__":
    unittest.main()
