#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${RELEASE_ROOT:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
TAG_MODE="auto"
REQUESTED_TAG=""

usage() {
    cat <<'EOF'
Usage: verify-release-metadata.sh [--tag <tag> | --no-tag]

Validate gradle/version.properties, the matching CHANGELOG section, and
versionCode monotonicity across GKD-SDP v2+ tags. With no option, an exact
tag on the current commit is validated when one exists.
EOF
}

error() {
    printf 'release metadata error: %s\n' "$1" >&2
    exit 1
}

while (($# > 0)); do
    case "$1" in
        --tag)
            (($# >= 2)) || error "--tag requires a tag name"
            TAG_MODE="explicit"
            REQUESTED_TAG="$2"
            shift 2
            ;;
        --no-tag)
            TAG_MODE="none"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            error "unknown argument: $1"
            ;;
    esac
done

VERSION_FILE="${ROOT_DIR}/gradle/version.properties"
CHANGELOG_FILE="${ROOT_DIR}/CHANGELOG.md"
[[ -f "$VERSION_FILE" ]] || error "missing ${VERSION_FILE}"
[[ -f "$CHANGELOG_FILE" ]] || error "missing ${CHANGELOG_FILE}"

while IFS= read -r version_line || [[ -n "$version_line" ]]; do
    [[ -z "$version_line" || "$version_line" == \#* || "$version_line" == \!* ]] && continue
    [[ "$version_line" =~ ^[A-Za-z][A-Za-z0-9]*=[^=]*$ ]] \
        || error "${VERSION_FILE} contains a non-canonical property line"
done < "$VERSION_FILE"

read_property() {
    local key="$1"
    awk -F= -v wanted="$key" '
        $1 == wanted {
            value = substr($0, index($0, "=") + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            print value
            exit
        }
    ' "$VERSION_FILE"
}

for property_name in versionName versionCode upstreamBase upstreamVersionCode; do
    property_count="$(awk -F= -v wanted="$property_name" '$1 == wanted { count++ } END { print count + 0 }' "$VERSION_FILE")"
    [[ "$property_count" == "1" ]] \
        || error "${VERSION_FILE} must contain exactly one ${property_name} entry (found ${property_count})"
done

version_name="$(read_property versionName)"
version_code="$(read_property versionCode)"
upstream_base="$(read_property upstreamBase)"
upstream_version_code="$(read_property upstreamVersionCode)"

[[ "$version_name" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.(0|[1-9][0-9]*))?$ ]] \
    || error "versionName must be SemVer with an optional alpha/beta/rc suffix: ${version_name:-<empty>}"
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] \
    || error "versionCode must be a positive decimal integer: ${version_code:-<empty>}"
[[ "$upstream_version_code" =~ ^[1-9][0-9]*$ ]] \
    || error "upstreamVersionCode must be a positive decimal integer: ${upstream_version_code:-<empty>}"
((10#${version_code} > 10#${upstream_version_code})) \
    || error "versionCode ${version_code} must be greater than upstreamVersionCode ${upstream_version_code}"
[[ "$upstream_base" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || error "upstreamBase must be a stable SemVer: ${upstream_base:-<empty>}"

if ! grep -Eq "^## \\[${version_name//./\\.}\\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" "$CHANGELOG_FILE"; then
    error "CHANGELOG.md is missing the dated heading for ${version_name}"
fi

current_tag=""
case "$TAG_MODE" in
    explicit)
        current_tag="$REQUESTED_TAG"
        ;;
    auto)
        current_tag="$(git -C "$ROOT_DIR" describe --tags --exact-match 2>/dev/null || true)"
        ;;
    none)
        ;;
esac

if [[ -n "$current_tag" ]]; then
    [[ "$current_tag" == "v${version_name}" ]] \
        || error "tag ${current_tag} does not match versionName ${version_name}; expected v${version_name}"
fi

version_code_number=$((10#${version_code}))
while IFS= read -r previous_tag; do
    [[ -n "$previous_tag" ]] || continue
    [[ "$previous_tag" =~ ^v(2|[3-9]|[1-9][0-9]+)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.(0|[1-9][0-9]*))?$ ]] || continue
    [[ "$previous_tag" == "$current_tag" ]] && continue

    previous_properties="$(git -C "$ROOT_DIR" show "${previous_tag}:gradle/version.properties" 2>/dev/null || true)"
    [[ -n "$previous_properties" ]] || error "${previous_tag} is an SDP tag without gradle/version.properties"
    previous_code="$(printf '%s\n' "$previous_properties" | awk -F= '$1 == "versionCode" {gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2; exit}')"
    [[ "$previous_code" =~ ^[1-9][0-9]*$ ]] \
        || error "${previous_tag} has an invalid versionCode: ${previous_code:-<empty>}"
    previous_code_number=$((10#${previous_code}))
    ((version_code_number > previous_code_number)) \
        || error "versionCode ${version_code} must be greater than ${previous_code} from ${previous_tag}"
done < <(git -C "$ROOT_DIR" tag --list 'v*' --sort=version:refname)

printf 'release metadata ok: versionName=%s versionCode=%s upstreamBase=%s upstreamVersionCode=%s' \
    "$version_name" "$version_code" "$upstream_base" "$upstream_version_code"
if [[ -n "$current_tag" ]]; then
    printf ' tag=%s' "$current_tag"
fi
printf '\n'
