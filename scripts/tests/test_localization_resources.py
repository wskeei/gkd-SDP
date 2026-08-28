from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-localization-resources.py"


def write_xml(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class LocalizationResourceTest(unittest.TestCase):
    def run_verifier(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                "python3",
                str(VERIFIER),
                "--values",
                str(root / "values/strings.xml"),
                "--values-en",
                str(root / "values-en/strings.xml"),
            ],
            capture_output=True,
            text=True,
        )

    def test_accepts_matching_keys_and_format_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_xml(root / "values/strings.xml", "<resources><string name='count'>%1$s 个</string></resources>")
            write_xml(root / "values-en/strings.xml", "<resources><string name='count'>%1$s items</string></resources>")
            result = self.run_verifier(root)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_missing_english_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_xml(root / "values/strings.xml", "<resources><string name='count'>%1$s 个</string></resources>")
            write_xml(root / "values-en/strings.xml", "<resources></resources>")
            result = self.run_verifier(root)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing en string: count", result.stderr)


if __name__ == "__main__":
    unittest.main()
