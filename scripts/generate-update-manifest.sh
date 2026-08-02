#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${RELEASE_ROOT:-$(cd -- "${SCRIPT_DIR}/.." && pwd)}"
APK=""
TAG=""
OUTPUT=""
NOTES_FILE=""

error() {
    printf 'update manifest error: %s\n' "$1" >&2
    exit 1
}

while (($# > 0)); do
    case "$1" in
        --apk)
            (($# >= 2)) || error "--apk requires a file"
            APK="$2"
            shift 2
            ;;
        --tag)
            (($# >= 2)) || error "--tag requires a tag name"
            TAG="$2"
            shift 2
            ;;
        --output)
            (($# >= 2)) || error "--output requires a file"
            OUTPUT="$2"
            shift 2
            ;;
        --notes-file)
            (($# >= 2)) || error "--notes-file requires a file"
            NOTES_FILE="$2"
            shift 2
            ;;
        -h|--help)
            printf '%s\n' 'Usage: generate-update-manifest.sh --apk <apk> --tag <vX.Y.Z> --output <update.json> [--notes-file <file>]'
            exit 0
            ;;
        *)
            error "unknown argument: $1"
            ;;
    esac
done

[[ -n "$APK" && -f "$APK" ]] || error "APK file does not exist: ${APK:-<empty>}"
[[ "$(basename -- "$APK")" == *.apk ]] || error "APK input must end in .apk"
[[ "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.(0|[1-9][0-9]*))?$ ]] \
    || error "tag must be vX.Y.Z with an optional prerelease suffix: ${TAG:-<empty>}"
[[ -n "$OUTPUT" ]] || error "--output is required"
[[ -d "$(dirname -- "$OUTPUT")" ]] || error "output directory does not exist: $(dirname -- "$OUTPUT")"
if [[ -n "$NOTES_FILE" ]]; then
    [[ -f "$NOTES_FILE" ]] || error "notes file does not exist: $NOTES_FILE"
fi

RELEASE_ROOT="$ROOT_DIR" "$SCRIPT_DIR/verify-release-metadata.sh" --tag "$TAG" >/dev/null

read_property() {
    local key="$1"
    awk -F= -v wanted="$key" '
        $1 == wanted {
            value = substr($0, index($0, "=") + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            print value
            exit
        }
    ' "${ROOT_DIR}/gradle/version.properties"
}

version_name="$(read_property versionName)"
version_code="$(read_property versionCode)"
apk_name="$(basename -- "$APK")"
file_size="$(wc -c < "$APK" | tr -d '[:space:]')"
if command -v sha256sum >/dev/null 2>&1; then
    sha256="$(sha256sum "$APK" | awk '{print $1}')"
else
    sha256="$(shasum -a 256 "$APK" | awk '{print $1}')"
fi

if [[ -n "$NOTES_FILE" ]]; then
    changelog="$(<"$NOTES_FILE")"
else
    changelog="$(awk -v version="$version_name" '
        BEGIN { in_section = 0 }
        $0 ~ "^## \\[" version "\\] - " { in_section = 1; next }
        in_section && /^## / { exit }
        in_section { print }
    ' "${ROOT_DIR}/CHANGELOG.md")"
fi
[[ -n "${changelog//[[:space:]]/}" ]] || error "release notes for ${version_name} are empty"

MANIFEST_CHANGELOG="$changelog" \
MANIFEST_DOWNLOAD_URL="https://github.com/wskeei/gkd-SDP/releases/download/${TAG}/${apk_name}" \
MANIFEST_FILE_SIZE="$file_size" \
MANIFEST_SHA256="$sha256" \
MANIFEST_VERSION_CODE="$version_code" \
MANIFEST_VERSION_NAME="$version_name" \
MANIFEST_OUTPUT="$OUTPUT" \
python3 - <<'PY'
import json
import os
from pathlib import Path

manifest = {
    "versionCode": int(os.environ["MANIFEST_VERSION_CODE"]),
    "versionName": os.environ["MANIFEST_VERSION_NAME"],
    "changelog": os.environ["MANIFEST_CHANGELOG"],
    "downloadUrl": os.environ["MANIFEST_DOWNLOAD_URL"],
    "fileSize": int(os.environ["MANIFEST_FILE_SIZE"]),
    "sha256": os.environ["MANIFEST_SHA256"].lower(),
    "versionLogs": [],
}
Path(os.environ["MANIFEST_OUTPUT"]).write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
PY

printf 'update manifest generated: %s\n' "$OUTPUT"
