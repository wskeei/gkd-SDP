from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-sensitive-output-policy.py"


class SensitiveOutputPolicyTest(unittest.TestCase):
    def test_v210_changes_pass(self):
        result = subprocess.run(
            ["python3", str(VERIFIER)], cwd=ROOT, capture_output=True, text=True
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_standalone_tree_rejects_raw_logging_and_throwable_ui(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "app/src/main/kotlin/example/Unsafe.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """
                package example
                import android.util.Log
                fun unsafe(error: Throwable) {
                    Log.e("unsafe", "failure")
                    error.printStackTrace()
                    toast(error.message ?: error.stackTraceToString())
                }
                """,
                encoding="utf-8",
            )
            result = subprocess.run(
                ["python3", str(VERIFIER), "--root", str(root)],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Unsafe.kt", result.stdout)
        self.assertIn("android.util.Log", result.stdout)
        self.assertIn("Throwable detail in user interface", result.stdout)


if __name__ == "__main__":
    unittest.main()
