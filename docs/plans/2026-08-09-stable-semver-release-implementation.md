# Stable SemVer Release Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the deprecated `latest` Release/tag, enforce stable Semantic Versioning, and publish verified stable `v2.1.0` with `versionCode=99`.

**Architecture:** Release metadata and manifest scripts enforce stable `X.Y.Z` inputs while retaining historical beta tags only for monotonicity checks. CI runs the tooling contracts, the Release workflow creates stable Draft Releases only, and documentation defines one version-selection source of truth.

**Tech Stack:** Bash, Python `unittest`, Git, GitHub Actions, GitHub CLI, Android version metadata, GitHub Releases.

---

### Task 1: Record the approved design and execution plan

**Files:**

- Create: `docs/plans/2026-08-09-stable-semver-release-design.md`
- Create: `docs/plans/2026-08-09-stable-semver-release-implementation.md`

**Steps:**

1. Record `v2.1.0`/`versionCode=99`, stable-only SemVer, historical-beta preservation, and exact `latest` retirement.
2. Run `git diff --check`.
3. Commit with `docs: define stable release versioning`.

### Task 2: Add failing stable-version release contracts

**Files:**

- Modify: `scripts/test-verify-release-metadata.sh`
- Create: `scripts/tests/test_stable_release_policy.py`

**Steps:**

1. Convert successful fixtures to stable `X.Y.Z` versions.
2. Add a test that a current `2.1.0-beta.1` version is rejected.
3. Add tests that release-PR validation rejects reused `versionCode` and a SemVer core older than an existing stable tag while accepting historical beta tags.
4. Add source contracts for stable manifest tags, stable workflow publication, CI tooling tests, and a stable-only README badge.
5. Run the shell and Python tests and record the expected failures against current production files.

### Task 3: Enforce stable versions in scripts and workflows

**Files:**

- Modify: `scripts/verify-release-metadata.sh`
- Modify: `scripts/generate-update-manifest.sh`
- Modify: `scripts/test-generate-update-manifest.sh`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

**Steps:**

1. Restrict Android build metadata, release metadata, and update tags to stable `X.Y.Z`.
2. Apply history monotonicity checks in `--no-tag`, auto, and explicit-tag modes while recognizing historical prerelease tags.
3. Run both release-tooling test suites in CI.
4. Remove the prerelease branch from Release creation and always mark the stable Draft as GitHub Latest.
5. Remove `include_prereleases` from the release badge.
6. Run the new tests to GREEN, then all script tests and `git diff --check`.
7. Commit with `build: enforce stable release versions`.

### Task 4: Publish the permanent version-selection rules

**Files:**

- Modify: `docs/releasing.md`
- Modify: `docs/testing/release-smoke-checklist.md`
- Modify: `docs/maintenance/github-settings.md`
- Modify: `docs/maintenance/recovery-runbook.md`
- Modify: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Modify: `AGENTS.md`
- Modify: `SECURITY.md`
- Modify: `SUPPORT.md`
- Modify: `GOVERNANCE.md`

**Steps:**

1. Define MAJOR, MINOR, and PATCH by compatibility and highest included impact.
2. Establish Nightly as the testing channel and prohibit rolling `latest` and new public prerelease tags.
3. Replace beta examples and patch recovery guidance with stable examples.
4. Preserve historical beta references that document actual migrations or old releases.
5. Run policy text searches and `git diff --check`.
6. Commit with `docs: adopt stable semantic versioning`.

### Task 5: Retire the exact deprecated GitHub target

**External targets:**

- GitHub Release tag: `latest`
- Git tag: `refs/tags/latest`

**Steps:**

1. Record the Release name, publication date, assets, tag object, and peeled commit.
2. Delete only the Release whose `tagName` is exactly `latest`.
3. Delete only remote and local `refs/tags/latest`.
4. Verify `gh release view latest`, `git ls-remote --tags origin refs/tags/latest`, and `git tag --list latest` all return absent.
5. Do not delete or rewrite any versioned beta Release/tag.

### Task 6: Prepare stable `v2.1.0` metadata

**Files:**

- Modify: `gradle/version.properties`
- Modify: `CHANGELOG.md`

**Steps:**

1. Set `versionName=2.1.0` and `versionCode=99`.
2. Add dated `2.1.0` Changed entries for stable SemVer and rolling-snapshot retirement.
3. Update comparison links from `v2.0.0-beta.6` to `v2.1.0`.
4. Run metadata, manifest, Python, formatting, persistence, and dependency checks.
5. Commit with `chore: prepare v2.1.0`.

### Task 7: Review, push, and merge the release-policy PR

**Steps:**

1. Perform an independent code review of `origin/main...HEAD`.
2. Push `codex/stable-semver-release` and create a PR to `main`.
3. Wait for `quality`, `build`, `dependency-review`, and CodeQL.
4. Fix every critical or important issue and repeat checks.
5. Merge with a merge commit and verify all branch commits are ancestors of `origin/main`.
6. Wait for merged-main CI, CodeQL, Nightly, and dependency graph to succeed.

### Task 8: Tag, build, verify, and publish stable `v2.1.0`

**Steps:**

1. Create annotated `v2.1.0` on the merged `origin/main` commit.
2. Run tag-aware release metadata tests and push the tag.
3. Wait for Release workflow success.
4. Verify the Draft is stable (`isPrerelease=false`) and contains only APK, `update.json`, and `SHA256SUMS.txt`.
5. Download assets into `mktemp -d`; verify checksums, update manifest, signature workflow step, and provenance attestation.
6. Publish the Draft with `isDraft=false` and stable/latest status.
7. Recheck tag/main alignment, release assets, old `latest` absence, and preserved user workspace state.
