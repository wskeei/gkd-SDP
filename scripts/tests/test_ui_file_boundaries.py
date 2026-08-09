import importlib.util
from pathlib import Path
import tempfile
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
        self.assertIn("Screen.kt", MODULE.PAGE_FILES)
        self.assertIn("Sections.kt", MODULE.PAGE_FILES)
        self.assertIn("ServiceHost.kt", MODULE.OVERLAY_FILES)
        self.assertNotIn("ServiceHostLegacy.kt", MODULE.OVERLAY_FILES)
        self.assertEqual(MODULE.MAX_LINES, 500)
        self.assertEqual(MODULE.MAX_COMPOSABLE_LINES, 180)

    def _fixture(self, directory: Path, expected: set[str]) -> None:
        for name in expected:
            (directory / name).write_text("package fixture\n", encoding="utf-8")

    def test_oversized_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            self._fixture(directory, MODULE.PAGE_FILES)
            (directory / "Sections2.kt").write_text("package fixture\n" + "x\n" * 500, encoding="utf-8")
            errors = MODULE.verify_module(directory, MODULE.PAGE_FILES, "fixture/page")
            self.assertTrue(any("Sections2.kt has 501 lines" in error for error in errors))

    def test_composable_span_uses_balanced_braces_and_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            self._fixture(directory, MODULE.PAGE_FILES)
            body = "\n".join(f"    Text(\"line {i}\")" for i in range(181))
            source = f"package fixture\n\n@Composable\nfun TooLong() {{\n{body}\n}}\n"
            target = directory / "Sections2.kt"
            target.write_text(source, encoding="utf-8")
            spans = MODULE.composable_spans(target)
            self.assertEqual(len(spans), 1)
            self.assertGreater(spans[0][1] - spans[0][0] + 1, MODULE.MAX_COMPOSABLE_LINES)
            errors = MODULE.verify_module(directory, MODULE.PAGE_FILES, "fixture/page")
            self.assertTrue(any("composable near line 3 spans" in error for error in errors))

    def test_empty_boundary_and_legacy_host_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            self._fixture(directory, MODULE.OVERLAY_FILES)
            (directory / "Presenter.kt").write_text(
                "package fixture\nobject FakeBoundary {}\n", encoding="utf-8"
            )
            (directory / "ServiceHostLegacy.kt").write_text(
                "package fixture\nclass Legacy\n", encoding="utf-8"
            )
            errors = MODULE.verify_module(directory, MODULE.OVERLAY_FILES, "fixture/overlay")
            self.assertTrue(any("empty boundary placeholder" in error for error in errors))
            self.assertTrue(any("ServiceHostLegacy.kt" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
