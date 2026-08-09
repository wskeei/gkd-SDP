import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class StableReleasePolicyTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_metadata_verifier_requires_stable_version_names(self) -> None:
        source = self.read("scripts/verify-release-metadata.sh")
        self.assertIn("versionName must be stable SemVer X.Y.Z", source)

    def test_update_manifest_requires_stable_tags(self) -> None:
        source = self.read("scripts/generate-update-manifest.sh")
        self.assertIn("tag must be stable vX.Y.Z", source)

    def test_release_workflow_never_creates_a_prerelease(self) -> None:
        workflow = self.read(".github/workflows/release.yml")
        self.assertNotIn("--prerelease", workflow)
        self.assertIn("release_args+=(--latest)", workflow)

    def test_ci_runs_both_release_tooling_contracts(self) -> None:
        workflow = self.read(".github/workflows/ci.yml")
        self.assertIn("bash scripts/test-verify-release-metadata.sh", workflow)
        self.assertIn("bash scripts/test-generate-update-manifest.sh", workflow)

    def test_readme_badge_excludes_prereleases(self) -> None:
        readme = self.read("README.md")
        self.assertNotIn("include_prereleases", readme)


if __name__ == "__main__":
    unittest.main()
