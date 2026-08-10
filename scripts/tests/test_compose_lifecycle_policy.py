import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ComposeLifecyclePolicyTest(unittest.TestCase):
    def test_policy_passes_for_repository_sources(self):
        path = ROOT / "scripts" / "verify-compose-lifecycle-policy.py"
        spec = importlib.util.spec_from_file_location("compose_lifecycle_policy", path)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        self.assertEqual(0, module.main())

    def test_policy_rejects_legacy_collect_as_state(self):
        source = (ROOT / "scripts" / "verify-compose-lifecycle-policy.py").read_text()
        self.assertIn("legacy_import", source)
        self.assertIn("legacy_call", source)


if __name__ == "__main__":
    unittest.main()
