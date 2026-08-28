import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/check-dev-environment.sh"


class DevEnvironmentPolicyTest(unittest.TestCase):
    def test_wrapper_and_environment_check_are_executable(self):
        self.assertTrue(os.access(ROOT / "gradlew", os.X_OK))
        self.assertTrue(os.access(SCRIPT, os.X_OK))

    def test_environment_check_names_required_capabilities(self):
        source = SCRIPT.read_text(encoding="utf-8")
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
            with self.subTest(capability=capability):
                self.assertIn(capability, source)

    def test_failure_output_does_not_echo_private_paths(self):
        with tempfile.TemporaryDirectory() as empty_path:
            private_home = str(Path(empty_path) / "private-home")
            private_jdk = str(Path(empty_path) / "private-jdk")
            result = subprocess.run(
                ["/bin/bash", str(SCRIPT), "--ci"],
                cwd=ROOT,
                env={"PATH": empty_path, "HOME": private_home, "JAVA_HOME": private_jdk},
                capture_output=True,
                text=True,
                check=False,
            )

        output = result.stdout + result.stderr
        self.assertNotEqual(0, result.returncode)
        self.assertIn("java", output)
        self.assertNotIn(private_home, output)
        self.assertNotIn(private_jdk, output)


if __name__ == "__main__":
    unittest.main()
