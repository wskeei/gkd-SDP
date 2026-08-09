import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


class DevEnvironmentPolicyTest(unittest.TestCase):
    def test_repository_pins_jdk_21(self) -> None:
        self.assertEqual("21\n", (REPO_ROOT / ".java-version").read_text())

    def test_gradle_wrapper_and_environment_check_are_executable(self) -> None:
        for relative_path in ("gradlew", "scripts/check-dev-environment.sh"):
            mode = (REPO_ROOT / relative_path).stat().st_mode
            self.assertTrue(mode & stat.S_IXUSR, relative_path)

    def test_environment_check_covers_required_capabilities(self) -> None:
        source = (REPO_ROOT / "scripts/check-dev-environment.sh").read_text()
        for capability in (
            "java",
            "JAVA_HOME",
            "Android SDK",
            "adb",
            "python3",
            "gh",
            "git",
            "gradlew",
            "--ci",
            "--android",
        ):
            self.assertIn(capability, source)

    def test_failure_output_names_capabilities_without_values(self) -> None:
        script = REPO_ROOT / "scripts/check-dev-environment.sh"
        with tempfile.TemporaryDirectory() as empty_path:
            environment = {
                "PATH": empty_path,
                "HOME": "/Users/private-developer",
                "JAVA_HOME": "/Users/private-developer/secret-jdk",
            }
            result = subprocess.run(
                ["/bin/bash", str(script), "--ci"],
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )

        output = result.stdout + result.stderr
        self.assertNotEqual(0, result.returncode)
        self.assertIn("java", output)
        self.assertIn("JAVA_HOME", output)
        self.assertNotIn(environment["HOME"], output)
        self.assertNotIn(environment["JAVA_HOME"], output)

    def test_ci_executes_the_committed_gradle_wrapper_directly(self) -> None:
        workflow = (REPO_ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("./gradlew", workflow)
        self.assertNotIn("chmod +x ./gradlew", workflow)


if __name__ == "__main__":
    unittest.main()
