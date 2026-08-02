#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
VERIFY_SCRIPT="${SCRIPT_DIR}/verify-release-metadata.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/gkd-sdp-release-metadata.XXXXXX")"

fail() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

assert_success() {
    local name="$1"
    shift
    if ! (cd "$TEST_ROOT" && RELEASE_ROOT="$TEST_ROOT" "$VERIFY_SCRIPT" "$@" >/dev/null); then
        fail "$name should succeed"
    fi
}

assert_failure() {
    local name="$1"
    shift
    if (cd "$TEST_ROOT" && RELEASE_ROOT="$TEST_ROOT" "$VERIFY_SCRIPT" "$@" >/dev/null 2>&1); then
        fail "$name should fail"
    fi
}

reset_repo() {
    rm -rf "$TEST_ROOT"
    mkdir -p "$TEST_ROOT"
    mkdir -p "$TEST_ROOT/gradle"
    git -C "$TEST_ROOT" init -q
    git -C "$TEST_ROOT" config user.email test@example.invalid
    git -C "$TEST_ROOT" config user.name "Release Metadata Test"
}

write_version() {
    local version_name="$1"
    local version_code="$2"
    cat > "$TEST_ROOT/gradle/version.properties" <<EOF
versionName=${version_name}
versionCode=${version_code}
upstreamBase=1.12.1
EOF
}

write_changelog() {
    local version_name="$1"
    cat > "$TEST_ROOT/CHANGELOG.md" <<EOF
# Changelog

## [Unreleased]

### Added

- Ongoing work.

## [${version_name}] - 2026-08-02

### Added

- Release metadata validation.
EOF
}

commit_fixture() {
    git -C "$TEST_ROOT" add gradle/version.properties CHANGELOG.md
    git -C "$TEST_ROOT" commit -q -m fixture
}

reset_repo
write_version "2.0.0-beta.1" 93
write_changelog "2.0.0-beta.1"
commit_fixture

assert_success "valid metadata without a tag" --no-tag
assert_success "matching tag" --tag v2.0.0-beta.1
assert_failure "mismatched tag" --tag v2.0.0

write_version "2.0.0-beta.1" 0
assert_failure "non-positive versionCode" --no-tag
write_version "2.0.0-beta.1" 93

cat > "$TEST_ROOT/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

### Added

- Missing version heading.
EOF
assert_failure "missing changelog heading" --no-tag
write_changelog "2.0.0-beta.1"

reset_repo
write_version "2.0.0-beta.0" 93
write_changelog "2.0.0-beta.0"
commit_fixture
git -C "$TEST_ROOT" tag v2.0.0-beta.0
write_version "2.0.0-beta.1" 94
write_changelog "2.0.0-beta.1"
commit_fixture
assert_success "versionCode increased from the previous SDP tag" --tag v2.0.0-beta.1

write_version "2.0.0-beta.2" 93
write_changelog "2.0.0-beta.2"
commit_fixture
assert_failure "versionCode reused from the previous SDP tag" --tag v2.0.0-beta.2

reset_repo
write_version "2.0.0-beta.1" 1
write_changelog "2.0.0-beta.1"
commit_fixture
git -C "$TEST_ROOT" tag v1.12.1
assert_success "upstream v1 tags are ignored" --no-tag

printf 'PASS: release metadata verifier tests\n'
