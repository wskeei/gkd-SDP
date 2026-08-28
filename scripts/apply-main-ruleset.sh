#!/usr/bin/env bash
set -euo pipefail

REQUIRED_CHECKS=(
  quality
  coverage
  visual-regression
  managed-device-api26
  managed-device-api35
  performance
  build
)

MODE="${1:---dry-run}"
case "$MODE" in
  --dry-run|--check|--apply) ;;
  *) echo "usage: $0 [--dry-run|--check|--apply]" >&2; exit 2 ;;
esac

command -v gh >/dev/null 2>&1 || { echo "gh CLI is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }

REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
RULESETS_JSON="$(gh api "repos/$REPO/rulesets")"

RULESET_ID="$(printf '%s' "$RULESETS_JSON" | python3 -c 'import json, sys; rulesets = json.load(sys.stdin); match = next((item for item in rulesets if item.get("name") == "main-protection"), None); print(match.get("id", "") if match else "")')"

TARGET_JSON="$(python3 - "${REQUIRED_CHECKS[@]}" <<'PY'
import json
import sys

checks = [{"context": name} for name in sys.argv[1:]]
print(json.dumps({
    "name": "main-protection",
    "enforcement": "active",
    "conditions": {"ref_name": {"include": ["refs/heads/main"], "exclude": []}},
    "rules": [
        {"type": "required_status_checks", "parameters": {
            "required_status_checks": checks,
            "strict_required_status_checks_policy": True,
        }},
        {"type": "required_linear_history"},
        {"type": "non_fast_forward"},
        {"type": "deletion"},
    ],
}))
PY
)"

if [ -n "$RULESET_ID" ]; then
  RULESETS_JSON="$(gh api "repos/$REPO/rulesets/$RULESET_ID")"
  CURRENT_NAMES="$(printf '%s' "$RULESETS_JSON" | python3 -c 'import json, sys; rules = json.load(sys.stdin).get("rules", []); print(json.dumps(next((rule.get("parameters", {}).get("required_status_checks", []) for rule in rules if rule.get("type") == "required_status_checks"), []), separators=(",", ":")))')"
  TARGET_NAMES="$(printf '%s' "$TARGET_JSON" | python3 -c 'import json, sys; rules = json.load(sys.stdin).get("rules", []); print(json.dumps(next((rule.get("parameters", {}).get("required_status_checks", []) for rule in rules if rule.get("type") == "required_status_checks"), []), separators=(",", ":")))')"
  if [ "$CURRENT_NAMES" = "$TARGET_NAMES" ]; then
    echo "main-protection is already in sync"
    exit 0
  fi
  echo "main-protection differs; target checks: $(printf '%s\n' "${REQUIRED_CHECKS[@]}" | tr '\n' ' ')"
  if [ "$MODE" = "--apply" ]; then
    gh api -X PUT "repos/$REPO/rulesets/$RULESET_ID" --input - <<<"$TARGET_JSON" >/dev/null
    echo "main-protection updated"
  fi
else
  echo "main-protection ruleset missing; creating target payload"
  if [ "$MODE" = "--apply" ]; then
    gh api -X POST "repos/$REPO/rulesets" --input - <<<"$TARGET_JSON" >/dev/null
    echo "main-protection created"
  fi
fi

if [ "$MODE" = "--check" ]; then
  echo "main-protection is out of sync" >&2
  exit 1
fi
