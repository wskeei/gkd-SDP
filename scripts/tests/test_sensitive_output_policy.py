import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
VERIFIER = REPO_ROOT / "scripts/verify-sensitive-output-policy.py"


class SensitiveOutputPolicyTest(unittest.TestCase):
    def run_verifier(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(VERIFIER), "--root", str(root)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_repository_has_no_forbidden_sensitive_output_apis(self) -> None:
        result = self.run_verifier(REPO_ROOT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_verifier_rejects_raw_logging_and_throwable_ui(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "app/src/main/kotlin/example/Unsafe.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """
                package example
                import android.util.Log
                fun unsafe(error: Throwable) {
                    error.printStackTrace()
                    println(error.stackTraceToString())
                    toast(error.message ?: error.stackTraceToString())
                }
                """,
            )

            result = self.run_verifier(root)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("Unsafe.kt", result.stdout)
        self.assertIn("android.util.Log", result.stdout)
        self.assertIn("printStackTrace", result.stdout)
        self.assertIn("Throwable detail in user interface", result.stdout)

    def test_verifier_rejects_dangerous_legacy_log_arguments(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "app/src/main/kotlin/example/Unsafe.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """
                package example
                fun unsafe(intent: Intent, request: Request) {
                    LogUtils.d(intent)
                    LogUtils.d(request.url)
                }
                """,
            )

            result = self.run_verifier(root)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("sensitive LogUtils argument", result.stdout)


if __name__ == "__main__":
    unittest.main()
