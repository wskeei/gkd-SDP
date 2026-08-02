from __future__ import annotations

import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "verify-security-dependency-report.py"
GENERATOR = ROOT / "scripts" / "generate-security-dependency-report.sh"
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

    def test_prerelease_target_versions_fail_closed(self) -> None:
        result = self.run_audit(FIXTURES / "security-dependencies-prerelease.txt")
        self.assertNotEqual(result.returncode, 0)
        output = result.stdout + result.stderr
        self.assertIn("io.netty:netty-codec", output)
        self.assertIn("org.bouncycastle:bcprov-jdk18on", output)
        self.assertIn("org.apache.commons:commons-lang3", output)

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

    def write_fake_gradlew(self, directory: Path) -> Path:
        fake_gradle = directory / "gradlew"
        fake_gradle.write_text(
            "#!/usr/bin/env python3\n"
            "import os\n"
            "import pathlib\n"
            "import sys\n"
            "counter = pathlib.Path('.fake-gradle-counter')\n"
            "count = int(counter.read_text()) + 1 if counter.exists() else 1\n"
            "counter.write_text(str(count))\n"
            "if os.environ.get('FAIL_AT') == str(count):\n"
            "    raise SystemExit(17)\n"
            "print('com.android.tools.build:gradle:9.3.0')\n"
            "print('io.netty:netty-codec:4.1.136.Final')\n",
            encoding="utf-8",
        )
        fake_gradle.chmod(fake_gradle.stat().st_mode | stat.S_IXUSR)
        return fake_gradle

    def test_generator_runs_all_reports_and_writes_completion_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            self.write_fake_gradlew(directory)
            result = subprocess.run(
                ["bash", str(GENERATOR)],
                cwd=directory,
                text=True,
                capture_output=True,
                check=False,
            )
            report = directory / "build" / "reports" / "security-dependencies.txt"
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue(report.is_file())
            report_text = report.read_text(encoding="utf-8")
            self.assertEqual(report_text.count("SECURITY_DEPENDENCY_SECTION="), 7)
            self.assertTrue(report_text.rstrip().endswith("SECURITY_DEPENDENCY_REPORT_COMPLETE=1"))

    def test_generator_does_not_write_completion_marker_after_gradle_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            self.write_fake_gradlew(directory)
            environment = os.environ.copy()
            environment["FAIL_AT"] = "3"
            result = subprocess.run(
                ["bash", str(GENERATOR)],
                cwd=directory,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            report = directory / "build" / "reports" / "security-dependencies.txt"
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue(report.is_file())
            self.assertNotIn("SECURITY_DEPENDENCY_REPORT_COMPLETE=1", report.read_text(encoding="utf-8"))

    def test_security_policy_declares_early_project_and_buildscript_hooks(self) -> None:
        policy = ROOT / "gradle" / "security-dependency-policy.settings.gradle.kts"
        self.assertTrue(policy.is_file())
        policy_text = policy.read_text(encoding="utf-8")
        self.assertIn("beforeProject", policy_text)
        self.assertIn("buildscript.configurations", policy_text)
        self.assertIn("configurations.configureEach", policy_text)
        self.assertIn("useVersion", policy_text)
        self.assertNotIn("resolutionStrategy.force", policy_text)

    def test_security_policy_covers_every_non_netty_security_family(self) -> None:
        policy = ROOT / "gradle" / "security-dependency-policy.settings.gradle.kts"
        policy_text = policy.read_text(encoding="utf-8")
        for floor_name in (
            "commonsLang3Floor",
            "httpcomponentsFloor",
            "jose4jFloor",
            "bouncycastleFloor",
            "jdom2Floor",
        ):
            self.assertIn(floor_name, policy_text)
        for coordinate in (
            'group == "org.apache.commons"',
            'group == "org.apache.httpcomponents"',
            'group == "org.bitbucket.b_c"',
            'group == "org.bouncycastle"',
            'group == "org.jdom"',
        ):
            self.assertIn(coordinate, policy_text)


if __name__ == "__main__":
    unittest.main()
