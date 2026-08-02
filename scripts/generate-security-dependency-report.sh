#!/usr/bin/env bash
set -euo pipefail

report_path="build/reports/security-dependencies.txt"
mkdir -p "$(dirname "$report_path")"
: > "$report_path"

if [[ ! -x ./gradlew ]]; then
  chmod +x ./gradlew
fi

run_report() {
  local section="$1"
  shift
  printf 'SECURITY_DEPENDENCY_SECTION=%s\n' "$section" >> "$report_path"
  ./gradlew --console=plain "$@" >> "$report_path"
  printf '\n' >> "$report_path"
}

run_report root-build-environment buildEnvironment
run_report app-build-environment :app:buildEnvironment
run_report hidden-api-build-environment :hidden_api:buildEnvironment
run_report selector-build-environment :selector:buildEnvironment
run_report app-dependencies :app:dependencies
run_report hidden-api-dependencies :hidden_api:dependencies
run_report selector-dependencies :selector:dependencies

printf 'SECURITY_DEPENDENCY_REPORT_COMPLETE=1\n' >> "$report_path"
printf 'Wrote %s\n' "$report_path"
