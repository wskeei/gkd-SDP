#!/usr/bin/env bash

set -u

mode="${1:---android}"
case "$mode" in
  --ci | --android) ;;
  *)
    printf '%s\n' 'usage: check-dev-environment.sh [--ci|--android]' >&2
    exit 2
    ;;
esac

script_dir="${0%/*}"
repo_root="$(cd "$script_dir/.." && pwd -P)"
missing=()

require_command() {
  local command_name="$1"
  local capability_name="$2"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    missing+=("$capability_name")
  fi
}

require_command java java

java_binary=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  java_binary="${JAVA_HOME}/bin/java"
else
  missing+=("JAVA_HOME")
fi

if [[ -z "$java_binary" ]] && command -v java >/dev/null 2>&1; then
  java_binary="$(command -v java)"
fi

if [[ -n "$java_binary" ]]; then
  java_version_output="$($java_binary -version 2>&1 || true)"
  if [[ ! "$java_version_output" =~ version\ \"21([.\"]|$) ]]; then
    missing+=("JDK 21")
  fi
fi

require_command python3 python3
require_command git git

if [[ ! -x "$repo_root/gradlew" ]]; then
  missing+=("gradlew executable")
fi

if [[ "$mode" == "--android" ]]; then
  require_command gh gh
  android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$android_sdk" || ! -d "$android_sdk" ]]; then
    missing+=("Android SDK")
  fi
  if ! command -v adb >/dev/null 2>&1 &&
    [[ -z "$android_sdk" || ! -x "$android_sdk/platform-tools/adb" ]]; then
    missing+=("adb")
  fi
fi

if (( ${#missing[@]} > 0 )); then
  for capability in "${missing[@]}"; do
    printf 'missing: %s\n' "$capability" >&2
  done
  exit 1
fi

printf '%s\n' 'development environment: ready'
