# Changelog

All notable GKD-SDP changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Enforce HTTPS-only transport for image preview requests, including HTTPS to
  HTTP redirects, and stop showing throwable messages in the image error state.
- Route capability links from notifications, permission repair, semantic deep
  links and legacy `gkd://page/3` to the runtime capability center.
- Scope the accessibility guard capability to gkd + Accessibility mode;
  Automation and play builds no longer advertise a guard action.
- Restore adaptive home content on medium/expanded widths and make a repeat tap
  on the current destination reset that destination without navigating again.
- Make settings search results actionable: results now navigate or scroll,
  highlight for 1.5 seconds, and persist recent items.
- Render the settings tab through the real settings surface instead of
  constructing an unused scaffold descriptor, and make the privacy backup
  chooser open outside MainActivity-only hosts.
- Migrate usage review, usage request, and trend chart text to Compose
  resources so screenshot previews and UI tests use stable localized strings.

### Changed

- Make performance verification fail closed on startup/frame thresholds, APK
  size and profile assets, release baseline profiles, and Compose stability.
- Activate the custom hardcoded-copy Lint registry and add Chinese/English
  string key and format-argument consistency verification.
- Read Kover include/exclude scope from the version-controlled files and fail
  on included classes with 0% line coverage.
- Make the test-quality and UI-boundary scripts reject placeholder UI flows,
  source-string contracts, empty tests, numeric section suffixes, and identity
  Presenters.
- Replace the privacy data, runtime capability, settings search, and
  self-control hub screenshot baselines with production `*Content` surfaces
  using deterministic fixtures.
- Extend screenshot coverage with real usage request and review dashboard
  surfaces, including dark and large-font variants.
- Wire privacy subscription/rules reset, self-control config reset, and
  delete-all paths through the data repository while active sessions block
  configuration deletion.
- Calibrate managed-emulator performance thresholds from the first real
  cold/warm macrobenchmark run and compute startup P95 from run samples when
  the benchmark file omits the percentile key.

## [2.2.1] - 2026-08-11

### Added

- Enable API 26 and API 35 Gradle Managed Device gates in CI.
- Add Baseline Profile generation for `gkdRelease` with cold and warm startup
  macrobenchmarks and performance report verification.
- Add a `baselineprofile` module and merge the generated Baseline Profile into
  the release APK.
- Add a main-protection ruleset sync helper with the full required check set.

### Fixed

- Extract validated archives reliably on Android API 26 by copying staged
  files into the destination instead of relying on directory move semantics.

### Changed

- Use the current AndroidX Benchmark plugin with AGP 9.3 and KVM-enabled
  emulators. Emulator performance metrics are recorded as automated pipeline
  evidence, not as physical-device performance claims.

### Testing

- Run `managed-device-api26`, `managed-device-api35`, `performance`, coverage,
  screenshot regression, and four-variant builds on every CI run.

## [2.2.0] - 2026-08-11

### Added

- Add the fixed Overview / Self-control / Rules / Settings information
  architecture with adaptive bottom navigation and navigation rail.
- Add a runtime capability center with one next action per state.
- Add searchable settings and a privacy & data page with local retention
  summaries, per-category deletion, full data deletion, and encrypted exports.
- Add encrypted, transactional backup v2 for settings, subscriptions,
  self-control configuration and history, and upstream history, with legacy
  import support and rollback recovery.
- Add whitelist support bundles that summarize diagnostics without including
  databases, raw settings, subscriptions, request reasons, URLs, screenshots,
  accessibility node content, contacts, cookies, or tokens.
- Align review ranges to rolling 24-hour, 7-day, and 30-day windows with the
  matching 1-hour, 6-hour, and 1-day buckets.

### Changed

- Make usage-request validation, duration presentation, and interval queries
  explicit pure-Kotlin contracts; interval insight queries are half-open and
  no longer load through a capped recent-record list.
- Harden local HTTP and shell-command interfaces with loopback-only defaults,
  pairing sessions, scoped bearer tokens, rate and size limits, and one-time
  command tokens.
- Restrict Android Auto Backup to non-sensitive theme and display preferences.
- Keep the countdown and request-reason overlays protected with `FLAG_SECURE`;
  the explicit screenshot mode only hides the overlay for ten seconds and does
  not alter third-party window flags.
- Make self-control clocks and dispatchers injectable, collect Compose state
  only while its lifecycle is started, restore semantic navigation across
  activity recreation, and split large UI and overlay hosts.
- Unify design tokens, accessible touch targets, English resources,
  state/error/save feedback, charts, and large-text presentation across core
  flows.

### Fixed

- Fail closed for malformed archives, backup decryption, remote session,
  command-token, WebView origin, and exported-component entry points.
- Keep diagnostic, crash, support-bundle, privacy summary, and deletion
  surfaces free of request reasons, URLs, selectors, accessibility node
  content, contacts, absolute paths, and credentials.

### Security

- Remove sensitive fields from production diagnostics and support bundles.
- Require explicit authorization for HTTP subscription sources and keep
  WebView and remote debug surfaces on fixed HTTPS origins.
- Add immutable explicit PendingIntent and exported-component contract checks
  for notification, widget, tile, file, scheme, and service entry points.

### Testing

- Replace placeholder/source-string contracts with behavioral JVM and
  instrumentation tests, Room 32→33 migration coverage, and test-quality
  policy checks.
- Add Compose screenshot regression references, Kover business-policy
  coverage gates, managed-device definitions, performance threshold
  contracts, and four-variant CI builds.
- Add navigation, capability, backup, data deletion, settings search, review
  dashboard, and usage-request instrumentation coverage.
- Managed-device and macrobenchmark execution remains deferred until a
  compatible AGP/plugin combination and KVM-capable CI runner are available;
  the device definitions and performance verification contracts are
  committed.

### Known limitations

- Physical-device/OEM validation is not claimed by automated release evidence.
  After this Release is public, the user should verify upgrade installation,
  Accessibility/Automation behavior, notification and overlay permissions,
  `FLAG_SECURE` screenshot composition, force-stop/back/home flows, and in-app
  update manually.

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

[2.2.1]: https://github.com/wskeei/gkd-SDP/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/wskeei/gkd-SDP/compare/v2.1.1...v2.2.0
[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.2.1...HEAD
[2.1.1]: https://github.com/wskeei/gkd-SDP/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.6...v2.1.0
[2.0.0-beta.6]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.5...v2.0.0-beta.6
[2.0.0-beta.5]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.4...v2.0.0-beta.5
[2.0.0-beta.4]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.4
[2.0.0-beta.3]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.3
[2.0.0-beta.2]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.2
[2.0.0-beta.1]: https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.1
