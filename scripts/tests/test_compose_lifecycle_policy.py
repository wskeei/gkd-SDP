import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/verify-compose-lifecycle-policy.py"
SPEC = importlib.util.spec_from_file_location("compose_lifecycle_policy", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class ComposeLifecyclePolicyTest(unittest.TestCase):
    def test_v210_overlay_contract_passes(self):
        self.assertEqual(0, MODULE.main([]))

    def test_policy_keeps_direct_collection_regression_guard(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("collectAsState", source)
        self.assertIn("setViewTreeLifecycleOwner", source)


if __name__ == "__main__":
    unittest.main()
