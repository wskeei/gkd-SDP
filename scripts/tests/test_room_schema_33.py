import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app" / "schemas" / "li.songe.gkd.sdp.db.AppDb"


class RoomSchema33Test(unittest.TestCase):
    def load(self, version: int):
        path = SCHEMA_DIR / f"{version}.json"
        self.assertTrue(path.is_file(), f"missing Room schema {path}")
        with path.open(encoding="utf-8") as stream:
            return json.load(stream)

    @staticmethod
    def table(schema, name):
        return next(
            table for table in schema["database"]["entities"]
            if table["tableName"] == name
        )

    def test_schema_32_is_present_and_unchanged_shape(self):
        schema = self.load(32)
        self.assertEqual(32, schema["database"]["version"])
        action_log = self.table(schema, "action_log")
        self.assertNotIn("outcome", {column["columnName"] for column in action_log["fields"]})

    def test_schema_33_contains_both_feature_shapes(self):
        schema = self.load(33)
        self.assertEqual(33, schema["database"]["version"])
        self.assertTrue(schema.get("formatVersion"))
        self.assertTrue(schema["database"].get("identityHash"))

        action_log = self.table(schema, "action_log")
        action_columns = {column["columnName"]: column for column in action_log["fields"]}
        for name in (
            "outcome",
            "matched_at",
            "subs_name_snapshot",
            "group_name_snapshot",
            "rule_name_snapshot",
        ):
            self.assertIn(name, action_columns)
        self.assertEqual("1", action_columns["outcome"]["defaultValue"])

        usage_record = self.table(schema, "usage_guard_record")
        usage_columns = {column["columnName"] for column in usage_record["fields"]}
        self.assertIn("last_usage_ended_at", usage_columns)
        self.assertIn("request_gap_ms", usage_columns)


if __name__ == "__main__":
    unittest.main()
