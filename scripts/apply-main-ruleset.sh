#!/usr/bin/env bash
set -euo pipefail

REQUIRED_CHECKS=(
  quality
  coverage
  visual-regression
  build
)

MODE="${1:---dry-run}"
case "$MODE" in
  --dry-run|--check|--apply) ;;
  *) echo "usage: $0 [--dry-run|--check|--apply]" >&2; exit 2 ;;
esac

REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
RULESETS_JSON="$(gh api "repos/$REPO/rulesets" --jq '[.[] | select(.name == "main-protection")] | first')"

if [ -z "$RULESETS_JSON" ] || [ "$RULESETS_JSON" = "null" ]; then
  RULESET_ID=""
else
  RULESET_ID="$(printf '%s' "$RULESETS_JSON" | jq -r '.id')"
fi

TARGET_JSON="$(jq -n --argjson checks "$(printf '%s\n' "${REQUIRED_CHECKS[@]}" | jq -R . | jq -s .)" '{
  name: "main-protection",
  enforcement: "active",
  conditions: {
    ref_name: { include: ["refs/heads/main"], exclude: [] }
  },
  rules: [
    { type: "pull_request", parameters: { required_status_checks: $checks, strict_required_status_checks_policy: true } },
    { type: "required_signatures" },
    { type: "non_fast_forward" },
    { type: "deletion" }
  ]
}')"

if [ -n "$RULESET_ID" ]; then
  CURRENT_NAMES="$(printf '%s' "$RULESETS_JSON" | jq -c '.rules[] | select(.type == "pull_request") | .parameters.required_status_checks')"
  TARGET_NAMES="$(printf '%s' "$TARGET_JSON" | jq -c '.rules[] | select(.type == "pull_request") | .parameters.required_status_checks')"
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
