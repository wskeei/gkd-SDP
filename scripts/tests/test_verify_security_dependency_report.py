from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "verify-security-dependency-report.py"
FIXTURES = Path(__file__).parent / "fixtures"


FLOORS = """\
netty4_1=4.1.136.Final
commonsLang3=3.18.0
httpcomponents4_5=4.5.14
jose4j=0.9.6
bouncycastleJdk18on=1.84
jdom2=2.0.6.1
"""


class SecurityDependencyReportTests(unittest.TestCase):
    def run_audit(self, input_path: Path, *, sbom: bool = False) -> subprocess.CompletedProcess[str]:
        with tempfile.NamedTemporaryFile("w", suffix=".properties", delete=False) as floors_file:
            floors_file.write(FLOORS)
            floors_path = Path(floors_file.name)
        try:
            argument = "--sbom" if sbom else "--report"
            return subprocess.run(
                [sys.executable, str(SCRIPT), argument, str(input_path), "--floors", str(floors_path)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
        finally:
            floors_path.unlink(missing_ok=True)

    def test_vulnerable_text_report_fails_and_lists_old_coordinates(self) -> None:
        result = self.run_audit(FIXTURES / "security-dependencies-vulnerable.txt")
        self.assertNotEqual(result.returncode, 0)
        output = result.stdout + result.stderr
        self.assertIn("io.netty:netty-codec", output)
        self.assertIn("org.bouncycastle:bcprov-jdk18on", output)
        self.assertIn("org.jdom:jdom2", output)

    def test_patched_text_report_passes_and_does_not_downgrade_future_versions(self) -> None:
        result = self.run_audit(FIXTURES / "security-dependencies-patched.txt")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_incomplete_text_report_fails_without_completion_marker(self) -> None:
        result = self.run_audit(FIXTURES / "security-dependencies-incomplete.txt")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SECURITY_DEPENDENCY_REPORT_COMPLETE", result.stdout + result.stderr)

    def test_vulnerable_sbom_fails(self) -> None:
        result = self.run_audit(FIXTURES / "security-sbom-vulnerable.json", sbom=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("io.netty:netty-codec", result.stdout + result.stderr)

    def test_patched_sbom_passes(self) -> None:
        result = self.run_audit(FIXTURES / "security-sbom-patched.json", sbom=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_httpmime_is_checked_even_when_httpclient_is_safe(self) -> None:
        report = FIXTURES / "httpmime-vulnerable.txt"
        report.write_text(
            "com.android.tools.build:gradle:9.3.0\n"
            "org.apache.httpcomponents:httpclient:4.5.14\n"
            "org.apache.httpcomponents:httpmime:4.5.6\n"
            "SECURITY_DEPENDENCY_REPORT_COMPLETE=1\n",
            encoding="utf-8",
        )
        try:
            result = self.run_audit(report)
        finally:
            report.unlink(missing_ok=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("httpmime", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
