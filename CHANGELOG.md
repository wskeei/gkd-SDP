# Changelog

All notable GKD-SDP changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Internal reliability

- Make self-control clocks and dispatchers injectable, collect Compose state only
  while its lifecycle is started, and restore semantic navigation across activity
  recreation.
- Keep UI and overlay host boundaries small and enforce the directory contract in CI;
  no user data format or runtime policy is changed by this internal refactor.

## [2.1.1] - 2026-08-09

### Fixed

- Restore foreground-app screenshots through an explicit ten-second countdown
  overlay hide action while keeping the remaining time and request reason
  protected whenever GKD-SDP's overlay is visible.

### Known limitations

- Android/OEM screenshot composition remains platform-controlled. Physical-device
  and OEM validation is left for the user after downloading this Release.

## [2.1.0] - 2026-08-09

This is the first stable GKD-SDP release. It promotes the tested
`2.0.0-beta.6` product state without changing Android runtime behavior, Room
schema, permissions, dependencies, signing identity, or application ID.

### Changed

- Adopt stable Semantic Versioning: incompatible changes increment MAJOR,
  compatible features increment MINOR, and compatible fixes or maintenance
  increment PATCH; routine test builds use Nightly instead of public betas.
- Make the versioned stable Release the only GitHub Latest target and retire the
  deprecated rolling Release and tag whose literal name was `latest`.

### Fixed

- Reject prerelease version names/tags, reused Android version codes, and
  backwards SemVer cores in release tooling, with the contracts enforced by CI.

### Known limitations

- Physical-device/OEM validation is left for the user after downloading this
  public stable Release and is not claimed by automated release evidence.

## [2.0.0-beta.6] - 2026-08-09

### Fixed

- Keep the active usage-request input visible above the software keyboard and
  preserve scrolling across tag, reason, and custom-duration editors.

### Security

- Preserve `FLAG_SECURE` while making the request overlay resize for the IME.

## [2.0.0-beta.5] - 2026-08-05

### Added

- Add a unified digital self-discipline review with overview, single-metric trends,
  distributions, coverage counts, and recent details for request and interception data.

### Changed

- Keep the “其他” usage tag last, move elapsed-use history to the top of the request form,
  and place ratio feedback beside the requested duration with a common-unit formula.
- Render low-volume interval samples point-for-point and aggregate only after the 24/28/30
  valid-sample limits; distinguish total records, valid samples, excluded rows, and chart points.

### Fixed

- Prevent missing-gap rows from disappearing from insight totals and prevent stable targets
  from splitting into duplicate ranking bars when their display label changes.

### Security

- Add no persistent fields and keep request reasons, URLs, selectors, and node text out of the
  new review projection, chart semantics, logs, and release notes.

### Known limitations

- Legacy rows and rows without a confirmed actual-use end remain unavailable for trusted gap
  and ratio calculations; no historical value is backfilled.

## [2.0.0-beta.4] - 2026-08-05

This prerelease makes interception attribution and usage rhythm easier to
understand while keeping the existing request, blocking, cooldown, and exit
behavior unchanged.

### Added

- Show the exact selector rule and safe app/URL blocker source on mounted interception overlays.
- Record mounted selector interceptions in Home trigger history with a separate outcome.
- Add rolling 24-hour, 7-day, and 30-day interval charts and the local “间用比” metric
  (request gap divided by requested duration), including current-value feedback in the form.
- Show the current interval, average, median, and sample count with readable chart summaries
  across request, app-blocker, selector-intercept, URL-intercept, and review surfaces.

### Changed

- Define usage-request gaps from the last observed end of use instead of the previous request time.
- Keep interval samples and exact-rule attribution bounded and local, with a non-destructive
  Room 32→33 migration and minimal projections for large datasets.

### Fixed

- Distinguish mounted interceptions from executed actions without calling the selector trigger,
  and preserve independent failure handling for ActionLog and interval persistence.
- Treat missing actual end times, clock rollback, future-dated rows, failed mounts, and malformed
  attribution as unavailable rather than inventing a duration or rule source.

### Security

- Store only bounded safe rule-name snapshots; exclude selectors, node text, actual URLs, patterns, and duplicate request reasons.
- Prune exact-rule latest-state keys after 90 days and cap them at 10,000 rows; action history remains capped at 500 rows.

### Known limitations

- Records from before this version do not have reliable actual-use end timestamps and are not
  backfilled into the new gap definition.
- Exact selector-rule interval samples start accumulating with this version because the new
  stable keys cannot safely merge legacy selector intervals.
- Force-stop, process termination, or runtime interruption can leave the actual end unknown;
  the UI shows unavailable instead of guessing.
- Physical-device/OEM validation is deferred until the maintainer downloads this public prerelease.

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

[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.1.1...HEAD
[2.1.1]: https://github.com/wskeei/gkd-SDP/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.6...v2.1.0
[2.0.0-beta.6]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.5...v2.0.0-beta.6
[2.0.0-beta.5]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.4...v2.0.0-beta.5
[2.0.0-beta.4]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.4
[2.0.0-beta.3]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.3
[2.0.0-beta.2]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.2
[2.0.0-beta.1]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.1
