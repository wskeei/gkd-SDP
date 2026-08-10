from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ReleaseNotesPrivacyTest(unittest.TestCase):
    def run_verifier(self, text: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "notes.txt"
            path.write_text(text, encoding="utf-8")
            return subprocess.run(
                ["python3", str(ROOT / "scripts/verify-release-notes-privacy.py"), str(path)],
                capture_output=True,
                text=True,
            )

    def test_accepts_public_links_and_stable_text(self):
        result = self.run_verifier(
            "Added stable release 2.2.0. See https://github.com/wskeei/gkd-SDP/releases."
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_credentials_paths_and_sensitive_examples(self):
        result = self.run_verifier(
            "Authorization: Bearer abc123 /Users/example/reason.txt"
        )
        self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
