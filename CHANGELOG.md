# Changelog

All notable GKD-SDP changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Continue recording fork-specific changes here before the next release.

### Changed

- None yet.

### Fixed

- None yet.

### Security

- None yet.

## [2.0.0-beta.1] - 2026-08-02

This is the first independently versioned GKD-SDP prerelease. It is based on
upstream GKD `v1.12.1`; the changes below describe the fork work included
since that base rather than repeating upstream release notes.

### Added

- Digital self-discipline features: focus mode, app and URL blocking, usage
  requests, automatic re-enable protection, and accessibility guarding.
- Elapsed-time awareness in usage-request forms and self-control intercept
  overlays, including the previous request/open interval.
- A reason overlay beside the usage countdown, protected from screenshots.
- Independent version metadata, release validation, and GitHub Actions release
  infrastructure for this fork.

### Changed

- Runtime ownership and handoff paths now keep self-discipline engines safe in
  both accessibility and automation/Shizuku modes.
- The accessibility service home status cannot be used to turn an active
  service off; disabling remains an explicit Android Settings action.

### Fixed

- Reconciled stale runtime events, foreground transitions, blocker decisions,
  and overlay launch failures across self-control modes.
- Stabilized coordinator and Room-related tests around deterministic runtime
  state transitions.

### Security

- Added accessibility-guard notifications and full-screen recovery behavior
  when the required service permission is disabled.
- Added screenshot protection for the usage reason overlay.

[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.1...HEAD
[2.0.0-beta.1]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.1
