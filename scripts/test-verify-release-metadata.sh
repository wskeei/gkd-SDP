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
upstreamVersionCode=92
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
write_version "2.1.0" 99

write_version "2.1.0" 1
assert_failure "versionCode below the upstream baseline" --no-tag
write_version "2.1.0" 99
write_changelog "2.1.0"
commit_fixture

assert_success "valid metadata without a tag" --no-tag
assert_success "matching tag" --tag v2.1.0
assert_failure "mismatched tag" --tag v2.1.1

write_version "2.1.0-beta.1" 100
write_changelog "2.1.0-beta.1"
assert_failure "current prerelease version is forbidden" --no-tag

write_version "2.1.0" 0
assert_failure "non-positive versionCode" --no-tag
write_version "2.1.0" 99

printf '\nversionCode=100\n' >> "$TEST_ROOT/gradle/version.properties"
assert_failure "duplicate versionCode" --no-tag
write_version "2.1.0" 99

printf ' versionCode=100\n' >> "$TEST_ROOT/gradle/version.properties"
assert_failure "non-canonical version property" --no-tag
write_version "2.1.0" 99

write_version "02.1.0" 99
assert_failure "non-SemVer leading zero" --no-tag
write_version "2.1.0" 99

cat > "$TEST_ROOT/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

### Added

- Missing version heading.
EOF
assert_failure "missing changelog heading" --no-tag
write_changelog "2.1.0"

reset_repo
write_version "2.1.0" 99
write_changelog "2.1.0"
commit_fixture
git -C "$TEST_ROOT" tag v2.1.0
assert_success "no-tag metadata may match the latest published version" --no-tag
write_version "2.1.0" 100
write_changelog "2.1.0"
commit_fixture
assert_failure "published versionName cannot be reused with a new versionCode" --no-tag

reset_repo
write_version "2.0.0-beta.6" 98
write_changelog "2.0.0-beta.6"
commit_fixture
git -C "$TEST_ROOT" tag v2.0.0-beta.6
write_version "2.1.0" 99
write_changelog "2.1.0"
commit_fixture
assert_success "stable metadata follows historical prerelease tags" --no-tag
assert_success "stable tag follows historical prerelease tags" --tag v2.1.0

write_version "2.1.0" 98
write_changelog "2.1.0"
commit_fixture
assert_failure "release PR rejects a reused versionCode" --no-tag

reset_repo
write_version "2.1.0" 99
write_changelog "2.1.0"
commit_fixture
git -C "$TEST_ROOT" tag v2.1.0
write_version "2.0.1" 100
write_changelog "2.0.1"
commit_fixture
assert_failure "release PR rejects an older SemVer core" --no-tag

reset_repo
write_version "2.1.0" 99
write_changelog "2.1.0"
commit_fixture
git -C "$TEST_ROOT" tag v1.12.1
assert_success "upstream v1 tags are ignored" --no-tag

printf 'PASS: release metadata verifier tests\n'
