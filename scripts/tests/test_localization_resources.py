from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-localization-resources.py"


def write_xml(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def run_verifier(values: Path, values_en: Path, plurals: Path, plurals_en: Path):
    return subprocess.run(
        [
            "python3",
            str(VERIFIER),
            "--values",
            str(values),
            "--values-en",
            str(values_en),
            "--plurals",
            str(plurals),
            "--plurals-en",
            str(plurals_en),
        ],
        capture_output=True,
        text=True,
    )


class LocalizationResourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.values = self.root / "values/strings.xml"
        self.values_en = self.root / "values-en/strings.xml"
        self.plurals = self.root / "values/plurals.xml"
        self.plurals_en = self.root / "values-en/plurals.xml"

    def tearDown(self):
        self.temp.cleanup()

    def write_consistent(self):
        write_xml(
            self.values,
            """<resources>
                <string name="ok">确定</string>
                <string name="count">%1$s 条</string>
            </resources>""",
        )
        write_xml(
            self.values_en,
            """<resources>
                <string name="ok">OK</string>
                <string name="count">%1$s items</string>
            </resources>""",
        )
        write_xml(
            self.plurals,
            """<resources>
                <plurals name="days"><item quantity="one">%1$d 天</item><item quantity="other">%1$d 天</item></plurals>
            </resources>""",
        )
        write_xml(
            self.plurals_en,
            """<resources>
                <plurals name="days"><item quantity="one">%1$d day</item><item quantity="other">%1$d days</item></plurals>
            </resources>""",
        )

    def test_accepts_consistent_resources(self):
        self.write_consistent()
        result = run_verifier(self.values, self.values_en, self.plurals, self.plurals_en)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Localization resources: OK", result.stdout)

    def test_rejects_missing_en_key(self):
        self.write_consistent()
        write_xml(
            self.values_en,
            """<resources>
                <string name="ok">OK</string>
            </resources>""",
        )
        result = run_verifier(self.values, self.values_en, self.plurals, self.plurals_en)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing en string: count", result.stderr)

    def test_rejects_empty_en_value(self):
        self.write_consistent()
        write_xml(
            self.values_en,
            """<resources>
                <string name="ok"></string>
                <string name="count">%1$s items</string>
            </resources>""",
        )
        result = run_verifier(self.values, self.values_en, self.plurals, self.plurals_en)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty en string: ok", result.stderr)

    def test_rejects_format_argument_mismatch(self):
        self.write_consistent()
        write_xml(
            self.values_en,
            """<resources>
                <string name="ok">OK</string>
                <string name="count">%1$s and %2$s</string>
            </resources>""",
        )
        result = run_verifier(self.values, self.values_en, self.plurals, self.plurals_en)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("format argument mismatch for count", result.stderr)

    def test_rejects_missing_plural_quantity(self):
        self.write_consistent()
        write_xml(
            self.plurals_en,
            """<resources>
                <plurals name="days"><item quantity="other">%1$d days</item></plurals>
            </resources>""",
        )
        result = run_verifier(self.values, self.values_en, self.plurals, self.plurals_en)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing en plurals quantity one: days", result.stderr)


if __name__ == "__main__":
    unittest.main()
