#!/usr/bin/env bash
set -euo pipefail

apk_path=""
serial=""
package_name="li.songe.gkd.sdp"
activity_name=".MainActivity"
keep_data="false"
while (($# > 0)); do
  case "$1" in
    --apk) apk_path="${2:?missing APK path}"; shift 2 ;;
    --serial) serial="${2:?missing adb serial}"; shift 2 ;;
    --package) package_name="${2:?missing package name}"; shift 2 ;;
    --activity) activity_name="${2:?missing activity name}"; shift 2 ;;
    --keep-data) keep_data="true"; shift ;;
    -h|--help) echo "Usage: $0 --apk APK --serial SERIAL [--package PACKAGE] [--activity ACTIVITY] [--keep-data]"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$apk_path" && -s "$apk_path" ]] || { echo "APK does not exist: $apk_path" >&2; exit 2; }
[[ -n "$serial" ]] || { echo "--serial is required" >&2; exit 2; }
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
adb_bin="${ADB:-$sdk_root/platform-tools/adb}"
[[ -x "$adb_bin" ]] || { echo "adb not found: $adb_bin" >&2; exit 2; }
adb() { "$adb_bin" -s "$serial" "$@"; }

adb get-state >/dev/null
if [[ "$keep_data" != "true" ]]; then adb uninstall "$package_name" >/dev/null 2>&1 || true; fi
adb install --no-incremental "$apk_path" >/dev/null
adb shell am force-stop "$package_name"
adb logcat -c >/dev/null 2>&1 || true
start_output="$(adb shell am start -W -S "${package_name}/${activity_name}" 2>&1)"
echo "$start_output"
rg -q '^Status: ok$' <<<"$start_output" || { echo "release APK launch failed" >&2; exit 1; }

pid=""
for _ in {1..20}; do
  pid="$(adb shell pidof "$package_name" 2>/dev/null | tr -d '\r' || true)"
  [[ -n "$pid" ]] && break
  sleep 1
done
if [[ -z "$pid" ]]; then
  echo "release APK process exited during cold start" >&2
  adb logcat -d -t 400 | tr -d '\r' | rg -n "${package_name}|FATAL EXCEPTION|ANR" || true
  exit 1
fi
activity_line=""
for _ in {1..20}; do
  activity_line="$(adb shell dumpsys activity activities | tr -d '\r' | rg 'mResumedActivity|topResumedActivity' | head -1 || true)"
  if rg -q "$package_name" <<<"$activity_line"; then break; fi
  sleep 1
done
echo "pid=$pid"
echo "resumed=$activity_line"
if ! rg -q "$package_name" <<<"$activity_line"; then
  echo "launcher activity was not resumed" >&2
  adb logcat -d -t 400 | tr -d '\r' | rg -n "${package_name}|FATAL EXCEPTION|ANR" || true
  exit 1
fi
crash_log="$(adb logcat -d -t 400 | tr -d '\r' | rg -n "FATAL EXCEPTION|ANR in $package_name|Process: $package_name" || true)"
if [[ -n "$crash_log" ]]; then echo "$crash_log" >&2; exit 1; fi
echo "release APK cold-start smoke passed: $package_name"
