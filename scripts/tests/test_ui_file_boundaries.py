import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/verify-ui-file-boundaries.py"
SPEC = importlib.util.spec_from_file_location("verify_ui_file_boundaries", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class UiFileBoundariesTest(unittest.TestCase):
    def test_repository_boundaries_pass(self):
        self.assertEqual(MODULE.main(), 0)

    def test_expected_boundary_sets_are_explicit(self):
        self.assertIn("Route.kt", MODULE.PAGE_FILES)
        self.assertIn("ServiceHost.kt", MODULE.OVERLAY_FILES)
        self.assertEqual(MODULE.MAX_LINES, 500)
        self.assertEqual(MODULE.MAX_COMPOSABLE_LINES, 180)


if __name__ == "__main__":
    unittest.main()
