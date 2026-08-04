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

## [2.0.0-beta.3] - 2026-08-04

This prerelease adds local interval feedback and review tools for the digital
self-discipline flows while keeping the existing request, blocking, cooldown,
and exit behavior unchanged.

### Added

- Show a live interval card in usage-request, app-blocker, selector-intercept,
  and URL-intercept overlays with up to five completed intervals, a growing
  current interval, average, median, and sample count.
- Expand “数字自律复盘” with today/7-day/30-day ranges, request/intercept
  filters, daily median charts, recent details, target rankings, and previous
  period comparison when enough samples exist.
- Add a concise home summary for today’s usage requests and intercepts.
- Store bounded local intercept history (90 days, at most 10,000 rows) and
  migrate Room schema 31 to 32 without copying sensitive request content.

### Changed

- Use stable per-app, per-selector-target, and per-URL-rule keys so intervals
  never cross unrelated applications or rules.
- Keep chart values available as readable text and keep all interval statistics
  local to the device.

### Fixed

- Preserve the existing elapsed-time anchor and successful-mount boundaries;
  failed or duplicate overlay starts do not create interval events.
- Isolate interval storage, chart, and review failures from the original
  request submission and blocking/exit paths.

### Security

- Interval history excludes application reasons, intercept messages, actual
  URLs, URL patterns, page text, screenshots, device identifiers, and telemetry.

## [2.0.0-beta.2] - 2026-08-03

This prerelease contains the runtime repair for digital self-discipline usage
requests.

### Fixed

- Reconcile the current foreground app after controlled-app configuration is
  persisted, so adding an app to normal or strict mode immediately evaluates
  its usage request even when the app was already in the foreground.
- Re-evaluate usage-request policy changes after saving the related settings,
  preventing stale runtime decisions from suppressing the request form.

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

[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.3...HEAD
[2.0.0-beta.3]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.3
[2.0.0-beta.2]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.2
[2.0.0-beta.1]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.1
