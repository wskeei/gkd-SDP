import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/verify-ui-file-boundaries.py"
SPEC = importlib.util.spec_from_file_location("ui_file_boundaries", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class UiFileBoundariesTest(unittest.TestCase):
    def test_v210_flat_layout_passes_with_explicit_legacy_budgets(self):
        self.assertEqual([], MODULE.verify(ROOT))

    def test_new_oversized_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "app/src/main/kotlin/li/songe/gkd/sdp/ui"
            directory.mkdir(parents=True)
            (directory / "NewPage.kt").write_text("x\n" * 501, encoding="utf-8")
            failures = MODULE.verify(root)
        self.assertTrue(any("NewPage.kt has 501 lines" in failure for failure in failures))


if __name__ == "__main__":
    unittest.main()
