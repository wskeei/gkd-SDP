# Stable SemVer Release Design

## Goal and scope

GKD-SDP will retire the deprecated rolling `latest` snapshot and move from the
`2.0.0-beta.N` line to normal stable Semantic Versioning. The exact historical
GitHub Release and lightweight tag named `latest` will be deleted. Existing
`v2.0.0-beta.1` through `v2.0.0-beta.6` tags and Releases remain immutable
history. This change does not modify Android runtime behavior, Room schema,
dependencies, permissions, signing identity, or application ID.

The first stable version is `2.1.0` with Android `versionCode=99`. It is a MINOR
release because it promotes the current backward-compatible product line and
formalizes its public release contract. It is not renamed to `2.0.0`, and no
existing beta tag or asset is moved or replaced.

## Version model

Public versions use stable `X.Y.Z` only. MAJOR (`X.0.0`) is reserved for an
incompatible product, data, permission, update, or runtime-contract change.
MINOR (`X.Y.0`) is used for backward-compatible user-visible capabilities or a
substantial compatible redesign. PATCH (`X.Y.Z`) is used for compatible bug
fixes, UI corrections, performance work, security maintenance, documentation,
and release-tooling fixes. When a release contains several change types, the
highest-impact rule wins. `versionCode` remains an independent positive Android
integer and must be greater than every historical GKD-SDP release code.

Nightly artifacts are the only routine testing channel. New public
`alpha`/`beta`/`rc` tags are rejected by release tooling. A public defect is
fixed forward with the next stable PATCH version; published tags and immutable
assets are never moved or replaced.

## Enforcement and release flow

The Android build metadata guard and metadata verifier accept only stable
current `versionName` values, while the verifier still reads historical beta
tags when checking semantic and `versionCode` monotonicity. Release-PR
`--no-tag` validation performs the same history checks as tag validation and
permits only an already-published version with the same `versionCode` to match
itself. The update-manifest generator accepts only stable `vX.Y.Z` tags.

CI runs both release-tooling shell suites. The Release workflow creates a plain
stable Draft with no prerelease or Latest flag; after asset inspection, the
publication command marks that stable Release as GitHub Latest. The README badge
excludes prereleases, and maintainer documentation prohibits a literal rolling
`latest` tag or Release. The normal signed workflow still owns compilation,
signing, package/version/certificate verification, checksums, attestation, and
immutable asset upload.

Before publishing `v2.1.0`, both feature/release PR checks and merged-main CI
must be green. The Draft assets are downloaded to a temporary directory and
independently checked before publication. Physical-device/OEM validation is not
claimed before release; the user performs it after downloading the stable APK.

## Failure handling

The deprecated `latest` target is resolved and recorded before deletion, then
both its Release and tag are verified absent. A failure before a new public
Release does not authorize moving a pushed tag. A failure after publication is
repaired with the next PATCH and a higher `versionCode`. Signing material,
request reasons, URLs, selectors, and device data never enter tests, logs,
release notes, or documentation.
