#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
GENERATE_SCRIPT="${SCRIPT_DIR}/generate-update-manifest.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/gkd-sdp-update-manifest.XXXXXX")"
mkdir -p "$TEST_ROOT/gradle"
git -C "$TEST_ROOT" init -q
git -C "$TEST_ROOT" config user.email test@example.invalid
git -C "$TEST_ROOT" config user.name "Update Manifest Test"

cat > "$TEST_ROOT/gradle/version.properties" <<'EOF'
versionName=2.1.0
versionCode=99
upstreamBase=1.12.1
upstreamVersionCode=92
EOF
cat > "$TEST_ROOT/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

- Work in progress.

## [2.1.0] - 2026-08-09

### Added

- A quoted "reason" and a newline-aware manifest.

## [Older]

- Do not include this section.
EOF
git -C "$TEST_ROOT" add .
git -C "$TEST_ROOT" commit -q -m fixture
git -C "$TEST_ROOT" tag v2.1.0
printf 'apk bytes with a checksum\n' > "$TEST_ROOT/app-release.apk"

RELEASE_ROOT="$TEST_ROOT" "$GENERATE_SCRIPT" \
    --apk "$TEST_ROOT/app-release.apk" \
    --tag v2.1.0 \
    --output "$TEST_ROOT/update.json"

python3 - "$TEST_ROOT/update.json" "$TEST_ROOT/app-release.apk" <<'PY'
import hashlib
import json
import pathlib
import sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text())
apk = pathlib.Path(sys.argv[2]).read_bytes()
assert manifest["versionCode"] == 99
assert manifest["versionName"] == "2.1.0"
assert manifest["downloadUrl"].endswith("/v2.1.0/app-release.apk")
assert manifest["fileSize"] == len(apk)
assert manifest["sha256"] == hashlib.sha256(apk).hexdigest()
assert 'quoted "reason"' in manifest["changelog"]
assert "Do not include this section" not in manifest["changelog"]
assert manifest["versionLogs"] == []
PY

if RELEASE_ROOT="$TEST_ROOT" "$GENERATE_SCRIPT" \
    --apk "$TEST_ROOT/app-release.apk" \
    --tag v2.1.1 \
    --output "$TEST_ROOT/mismatched.json" >/dev/null 2>&1; then
    printf 'FAIL: mismatched stable tag should fail\n' >&2
    exit 1
fi

cat > "$TEST_ROOT/gradle/version.properties" <<'EOF'
versionName=2.1.0-beta.1
versionCode=100
upstreamBase=1.12.1
upstreamVersionCode=92
EOF
cat > "$TEST_ROOT/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

- Work in progress.

## [2.1.0-beta.1] - 2026-08-09

### Added

- A prerelease that must be rejected by the stable release policy.
EOF
git -C "$TEST_ROOT" add gradle/version.properties CHANGELOG.md
git -C "$TEST_ROOT" commit -q -m prerelease-fixture
git -C "$TEST_ROOT" tag v2.1.0-beta.1

if RELEASE_ROOT="$TEST_ROOT" "$GENERATE_SCRIPT" \
    --apk "$TEST_ROOT/app-release.apk" \
    --tag v2.1.0-beta.1 \
    --output "$TEST_ROOT/prerelease.json" >/dev/null 2>&1; then
    printf 'FAIL: prerelease tag should fail\n' >&2
    exit 1
fi

printf 'PASS: update manifest generator tests\n'
