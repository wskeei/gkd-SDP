from pathlib import Path
import importlib.util
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/verify-localization-sources.py"
SPEC = importlib.util.spec_from_file_location("verify_localization_sources", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class LocalizationSourceTest(unittest.TestCase):
    def test_rejects_unmarked_cjk_literal(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "Bad.kt"
            path.write_text('val text = "中文"\n', encoding="utf-8")
            errors = MODULE.verify_file(path)
            self.assertTrue(any("unmarked CJK literal" in error for error in errors))

    def test_accepts_marked_cjk_literal(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "Good.kt"
            path.write_text(
                '// i18n-ignore: heuristic node text\nval text = "发消息"\n',
                encoding="utf-8",
            )
            self.assertEqual([], MODULE.verify_file(path))


if __name__ == "__main__":
    unittest.main()
