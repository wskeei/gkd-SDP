#!/usr/bin/env bash
set -euo pipefail

api="35"
declare -a apks=()
while (($# > 0)); do
  case "$1" in
    --api) api="${2:?missing API level}"; shift 2 ;;
    --apk) apks+=("${2:?missing APK path}"); shift 2 ;;
    -h|--help) echo "Usage: $0 --api API --apk APK [--apk APK ...]"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
(( ${#apks[@]} > 0 )) || { echo "at least one --apk is required" >&2; exit 2; }

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk_root" ]] || { echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2; exit 2; }
avdmanager_bin="$sdk_root/cmdline-tools/latest/bin/avdmanager"
emulator_bin="$sdk_root/emulator/emulator"
adb_bin="${ADB:-$sdk_root/platform-tools/adb}"
[[ -x "$avdmanager_bin" && -x "$emulator_bin" && -x "$adb_bin" ]] || { echo "Android tools are incomplete" >&2; exit 2; }
case "$(uname -m)" in
  arm64|aarch64) abi="arm64-v8a" ;;
  x86_64|amd64) abi="x86_64" ;;
  *) echo "unsupported host architecture: $(uname -m)" >&2; exit 2 ;;
esac
image="system-images;android-${api};google_apis;${abi}"
"$sdk_root/cmdline-tools/latest/bin/sdkmanager" --list_installed 2>/dev/null | rg -Fq "$image" || { echo "missing system image: $image" >&2; exit 2; }

smoke_root="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/gkd-release-smoke.XXXXXX")"
avd_name="gkd_release_smoke_${api}_$$"
avd_path="$smoke_root/avd"
port=5560
cleanup() { if [[ -n "${emulator_pid:-}" ]]; then kill "$emulator_pid" >/dev/null 2>&1 || true; fi; rm -rf "$smoke_root"; }
trap cleanup EXIT
mkdir -p "$avd_path"
printf 'no\n' | ANDROID_AVD_HOME="$smoke_root" "$avdmanager_bin" create avd --force --name "$avd_name" --package "$image" --device "pixel_6" --path "$avd_path" >/dev/null
ANDROID_AVD_HOME="$smoke_root" "$emulator_bin" -avd "$avd_name" -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -gpu swiftshader_indirect -port "$port" >"$smoke_root/emulator.log" 2>&1 &
emulator_pid=$!
serial="emulator-$port"
wait_for_device_seconds=90
for _ in $(seq 1 "$wait_for_device_seconds"); do
  if "$adb_bin" -s "$serial" get-state >/dev/null 2>&1; then break; fi
  sleep 1
done
"$adb_bin" -s "$serial" get-state >/dev/null 2>&1 || {
  echo "emulator did not become available within ${wait_for_device_seconds}s" >&2
  cat "$smoke_root/emulator.log" >&2
  exit 1
}
booted=""
for _ in {1..90}; do booted="$("$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"; [[ "$booted" == "1" ]] && break; sleep 2; done
[[ "$booted" == "1" ]] || { cat "$smoke_root/emulator.log"; exit 1; }
for apk in "${apks[@]}"; do bash "$(dirname "$0")/smoke-test-release-apk.sh" --apk "$apk" --serial "$serial"; done
