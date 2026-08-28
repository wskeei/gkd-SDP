from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "scripts/verify-kover-report.py"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def xml_report(line1=(0, 10), line2=(0, 10), branch1=(0, 5), branch2=(0, 5)):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<report name="kover">
  <package name="li.songe.gkd.sdp.privacy">
    <class name="DataInventoryRepository">
      <counter type="LINE" missed="{line1[0]}" covered="{line1[1]}"/>
      <counter type="BRANCH" missed="{branch1[0]}" covered="{branch1[1]}"/>
    </class>
  </package>
  <package name="li.songe.gkd.sdp.remote">
    <class name="RemoteSessionPolicy">
      <counter type="LINE" missed="{line2[0]}" covered="{line2[1]}"/>
      <counter type="BRANCH" missed="{branch2[0]}" covered="{branch2[1]}"/>
    </class>
  </package>
</report>"""


def run_verifier(root: Path, report: str, includes: str, excludes: str):
    xml = root / "kover.xml"
    inc = root / "includes.txt"
    exc = root / "excludes.txt"
    write(xml, report)
    write(inc, includes)
    write(exc, excludes)
    return subprocess.run(
        [
            "python3",
            str(VERIFIER),
            "--xml",
            str(xml),
            "--includes",
            str(inc),
            "--excludes",
            str(exc),
        ],
        capture_output=True,
        text=True,
    )


class KoverReportPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_accepts_complete_covered_report(self):
        result = run_verifier(
            self.root,
            xml_report(),
            "li.songe.gkd.sdp.privacy.*\nli.songe.gkd.sdp.remote.*\n",
            "androidx.compose.**\n",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Kover policy: OK", result.stdout)

    def test_rejects_zero_coverage_included_class(self):
        result = run_verifier(
            self.root,
            xml_report(line2=(10, 0)),
            "li.songe.gkd.sdp.privacy.*\nli.songe.gkd.sdp.remote.*\n",
            "androidx.compose.**\n",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("included class has 0% line coverage", result.stderr)

    def test_rejects_include_pattern_without_matching_class(self):
        result = run_verifier(
            self.root,
            xml_report(),
            "li.songe.gkd.sdp.backup.*\n",
            "androidx.compose.**\n",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("matches no report class", result.stderr)

    def test_rejects_broad_exclude(self):
        result = run_verifier(
            self.root,
            xml_report(),
            "li.songe.gkd.sdp.privacy.*\nli.songe.gkd.sdp.remote.*\n",
            "li.songe.gkd.sdp.ui.**\n",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Kover exclude is too broad", result.stderr)

    def test_rejects_duplicate_include(self):
        result = run_verifier(
            self.root,
            xml_report(),
            "li.songe.gkd.sdp.privacy.*\nli.songe.gkd.sdp.privacy.*\n",
            "androidx.compose.**\n",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("duplicate pattern", result.stderr)


if __name__ == "__main__":
    unittest.main()
