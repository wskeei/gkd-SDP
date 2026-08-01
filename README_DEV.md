# GKD-SDP Developer README

This document is for developers, collaborators, and coding agents working on this repository.

`GKD-SDP` is a fork of GKD that keeps the original selector/subscription automation core, then layers on self-control features intended to reduce compulsive phone usage. The repository still carries a lot of upstream naming (`gkd`, upstream URLs, selector terminology), so always verify whether a piece of code belongs to the original automation stack or to the self-discipline features added in this fork.

## What This Fork Adds

Compared with upstream GKD, this project is not only an accessibility automation app. It also contains several intervention systems built around the same runtime:

- `Focus Mode`: temporary or scheduled focus sessions with a whitelist, lock window, and blocking overlay.
- `App Blocker`: time-based blocking for specific apps or app groups.
- `URL Blocker`: browser URL detection via accessibility tree inspection, optional redirect, and intercept overlay.
- `Auto Re-enable`: periodic recovery of disabled rules/groups so restrictions are harder to permanently turn off.
- `Constraint / intercept management`: extra state used to lock or guard rule groups beyond the original subscription enable/disable model.

## Tech Stack

- Android app, Kotlin, Jetpack Compose, Compose Destinations
- Room for structured data
- File-backed JSON/TXT state flows for lightweight settings
- Kotlinx Serialization
- Ktor client/server
- Shizuku + hidden API stubs + Rikka Refine for privileged integrations
- Kotlin Multiplatform `selector` module for selector parsing/matching

Important versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Current Android/build settings:

- `applicationId`: `li.songe.gkd.sdp`
- `minSdk`: 26
- `compileSdk` / `targetSdk`: 37
- Kotlin target: JVM 11
- Product flavors: `gkd` and `play`

Practical note: Gradle task plans in this repo have been run with JDK 21, while the app itself targets Java 11 bytecode.

## Repository Shape

- [`app`](app): main Android application
- [`selector`](selector): selector parser/matcher shared logic, Kotlin Multiplatform
- [`hidden_api`](hidden_api): compile-only Android hidden API stubs used by Shizuku/refine code
- [`docs/plans`](docs/plans): implementation handoff notes and AI-oriented plans
- [`docs/releasing.md`](docs/releasing.md): versioning, signing and GitHub Release procedure
- [`docs/testing/release-smoke-checklist.md`](docs/testing/release-smoke-checklist.md): real-device release checks

Within `app`, the most important directories are:

- [`app/src/main/kotlin/li/songe/gkd/sdp/a11y`](app/src/main/kotlin/li/songe/gkd/sdp/a11y): accessibility runtime, activity tracking, rule engines, feature hooks
- [`app/src/main/kotlin/li/songe/gkd/sdp/service`](app/src/main/kotlin/li/songe/gkd/sdp/service): Android services, overlays, background loops
- [`app/src/main/kotlin/li/songe/gkd/sdp/ui`](app/src/main/kotlin/li/songe/gkd/sdp/ui): Compose pages and view models
- [`app/src/main/kotlin/li/songe/gkd/sdp/data`](app/src/main/kotlin/li/songe/gkd/sdp/data): Room entities, DAOs, DTOs, rule models
- [`app/src/main/kotlin/li/songe/gkd/sdp/store`](app/src/main/kotlin/li/songe/gkd/sdp/store): file-backed settings/state flows
- [`app/src/main/kotlin/li/songe/gkd/sdp/shizuku`](app/src/main/kotlin/li/songe/gkd/sdp/shizuku): privileged helpers and binder wrappers
- [`app/src/test`](app/src/test): JVM unit tests
- [`app/schemas`](app/schemas): exported Room schemas

## Runtime Architecture

The runtime model is easier to reason about if you separate it into four layers.

### 1. App bootstrap

[`app/src/main/kotlin/li/songe/gkd/sdp/App.kt`](app/src/main/kotlin/li/songe/gkd/sdp/App.kt) is the process entry point. On startup it initializes:

- file-backed store flows
- notification channels
- app/subscription state caches
- Shizuku integration
- auto re-enable loop
- accessibility whitelist / cleanup helpers

[`app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt`](app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt) hosts the Compose app and also calls `syncFixState()`, which refreshes permissions, service state, top-app state, and Shizuku-backed capabilities.

### 2. Accessibility and activity tracking

[`app/src/main/kotlin/li/songe/gkd/sdp/service/A11yService.kt`](app/src/main/kotlin/li/songe/gkd/sdp/service/A11yService.kt) is the main accessibility service base class.

At runtime:

1. Accessibility events enter `A11yService`.
2. [`A11yRuleEngine`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yRuleEngine.kt) consumes relevant events, keeps `topActivityFlow` in sync, queries the node tree, and executes upstream-style selector actions.
3. [`SdpRuntimeFeatureCoordinator`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/SdpRuntimeFeatureCoordinator.kt) is the single process-wide entry point for digital self-discipline features. Both `A11yService` and `AutomationService` attach their `A11yCommonImpl` owner through `A11yRuleEngine`; do not add feature hooks only to `A11yService`.

`A11yFeat.kt` remains for upstream/common event watchers (screenshots, subscription refresh, volume and screen state). It is not a second foreground-app collector.

Top activity detection is hybrid:

- accessibility events are the primary signal
- Shizuku task-stack queries are used as a fallback/correction path
- several caches and throttles exist because some apps are slow or inconsistent when exposing `rootInActiveWindow`

If you touch app-change logic, test both with and without Shizuku enabled.

### 3. Restriction engines

There are multiple engines, and they do not all trigger the same way.

- [`A11yRuleEngine`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yRuleEngine.kt): upstream selector/subscription matching and action execution
- [`FocusModeEngine`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt): reacts to foreground app changes and shows a full-screen focus overlay for non-whitelisted apps while a focus session or scheduled focus rule is active
- [`AppBlockerEngine`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/AppBlockerEngine.kt): reacts to foreground app changes and blocks configured apps/groups during active time windows
- [`UrlBlockerEngine`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/UrlBlockerEngine.kt): reacts to accessibility events inside supported browsers, reads the address bar, matches URL rules, optionally redirects, then may show an intercept overlay

This split matters. Not every restriction is a selector rule, and not every disable/enable action goes through the subscription system.

### 4. Overlays and services

Interventions are surfaced through Android services rather than only Compose screens.

Common examples:

- [`FocusOverlayService`](app/src/main/kotlin/li/songe/gkd/sdp/service/FocusOverlayService.kt)
- [`AppBlockerOverlayService`](app/src/main/kotlin/li/songe/gkd/sdp/service/AppBlockerOverlayService.kt)
- [`InterceptOverlayService`](app/src/main/kotlin/li/songe/gkd/sdp/service/InterceptOverlayService.kt)
- [`StatusService`](app/src/main/kotlin/li/songe/gkd/sdp/service/StatusService.kt)
- [`AutoReenableEnforcer`](app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt)

The app manifest is large because many behaviors are service-driven. See [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) before changing permissions or background behavior.

All self-control overlay starts/stops go through [`SelfControlOverlayLauncher`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/SelfControlOverlayLauncher.kt). It checks `Settings.canDrawOverlays`, classifies service-start failures, and callers only commit cooldown state after an `Accepted` result. The overlay services still guard their final `WindowManager.addView` call because a start request can be accepted while a ROM later rejects the window.

System navigation from self-control overlays uses [`A11yRuleEngine.performActionHome`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yRuleEngine.kt), which injects `KEYCODE_HOME` through Shizuku when possible and falls back to `GLOBAL_ACTION_HOME`. `SafeInputManager.key()` must return the actual down/up injection result; do not treat a non-null manager as success.

### Digital Self-Discipline / Usage Guard

An approved usage request is persisted as a `UsageGuardRecord`. Its
`reasonText` remains the source of truth for the active countdown reminder;
the overlay must not keep an independently editable reason.

The countdown service renders remaining time and reason in one movable
`TYPE_APPLICATION_OVERLAY` window. That window uses `FLAG_SECURE`, so neither
field should be readable in screenshots, screen recording, or non-secure
display output. Android/OEM capture behavior may produce a blank or black
protected region, or reject capture; the app does not promise reconstruction
of the third-party app pixels behind the secure window.

Screenshot protection requires physical-device/manual verification; JVM unit
tests only validate the configured flag contract.

### Accessibility Guard

The accessibility guard is a `gkd`-channel feature exposed under the Digital
Self-Discipline page. The `play` flavor keeps the shared code compiling, but
strict guard behavior is disabled there. Enabling the guard does **not** require
the app's accessibility component to be running; it requires the A11y mode plus
the following runtime capabilities:

- notification permission and an enabled notification channel for the reminder banners
- the `StatusService` special-use foreground service staying available
- the Android “draw over other apps” permission for the final blocking overlay
- the app's accessibility service state is read at runtime to decide whether a
  tracking session is needed (the guard may be enabled while that state is off)

The guard's production schedule is fixed at cumulative offsets of
`[15, 25, 30, 33, 35, 36]` minutes after accessibility is disabled. At `T+0`
the coordinator posts one ongoing status notification with a SystemUI
chronometer to the next checkpoint; the six high-priority stage notifications
remain separate. The notification opens `gkd://page/4`, which routes to the
Digital Self-Discipline page. It uses local notifications and the existing
foreground service; it does not use FCM, WorkManager, exact alarms, or a
full-screen notification intent. The `StatusService` is therefore part of the
guard's runtime contract and should remain enabled while the feature is in use.

Only an explicit successful enable arms the persisted enrollment marker for
automatic recovery. A later user disable leaves that marker armed, so
`AutoReenableEnforcer` may restore the guard at its next legal check. Guard
disable is serialized and checks the read-only
`DigitalSelfDisciplineLockDao` plus the shared daily disable quota; any active
digital self-discipline lock blocks the action. The home-page “服务状态” A11y
switch is intentionally one-way: an on gesture starts/repairs the service, an
off gesture only explains the system-settings path and never calls
`AccessibilityService.disableSelf()`.

These platform limits are expected and should be reflected in tests and support guidance:

- A user-disabled notification channel or Do Not Disturb mode can prevent a top-of-screen reminder banner from appearing.
- Doze and OEM background scheduling can delay a check past its nominal checkpoint. On recovery, the coordinator emits only the latest reminder that is due; it does not replay every missed banner.
- Android places a package in the stopped state after “Force stop”; the app cannot restart itself until the user launches it again.
- Android 13+ exposes a foreground-service task-manager “Stop” action that ends the whole app process. Ordinary Back, Home, app switching, and dismissing the app from Recents are separate lifecycle paths and must be tested separately; do not label all of them “force stop”.
- System windows always sit above application overlays. The guard's “full-screen” prompt is therefore full-screen within the region Android permits an application overlay to occupy.

For manual testing, keep the production constants in minutes. Inject a test clock/schedule into the coordinator, or use a debug-only development configuration with `[15s, 25s, 30s, 33s, 35s, 36s]`. Release builds must continue to assert the minute-based schedule, and the debug switch must not be persisted as a production user setting.

## Persistence Model

This project uses both Room and file-backed state. Do not assume all toggles belong in the database.

### Room

The main Room database is defined in [`app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`](app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt) and stored under the app external files directory at `db/gkd.db`.

High-level table groups:

- Upstream GKD-style data: subscriptions, snapshots, action/activity/a11y logs, app config
- Focus mode: `focus_rule`, `focus_session`
- App blocker: `app_group`, `block_time_rule`, `app_blocker_lock`
- URL blocker: `browser_config`, `url_block_rule`, `url_rule_group`, `url_time_rule`, `url_blocker_lock`
- Constraint/intercept state: `intercept_config`, `constraint_config`

When you change entities:

- keep Room schema export enabled
- update migrations in `AppDb`
- preserve legacy columns when needed for compatibility
- check [`app/schemas`](app/schemas) diffs as part of review

### File-backed store

[`app/src/main/kotlin/li/songe/gkd/sdp/store/StorageExt.kt`](app/src/main/kotlin/li/songe/gkd/sdp/store/StorageExt.kt) implements persistent `MutableStateFlow`s backed by JSON/TXT files.

Key points:

- `SettingsStore` lives in [`app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`](app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt)
- atomic writes use a temp file + move
- not every setting belongs in Room
- `privateStoreFolder` is separate from the regular external `store` folder

Filesystem locations are defined in [`app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt`](app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt):

- `db/`
- `store/`
- `subscription/`
- `snapshot/`
- `log/`
- `sh/`

`ExposeService` also writes a helper shell script to `sh/expose.sh`.

## Important Mental Models

### Upstream naming still exists

The root project name is still `gkd`, upstream URLs are still present in several constants, and some strings/docs still describe the original automation app. That is historical carry-over, not a reliable signal of current product intent.

### `enabled`, `locked`, and `intercepted` are different concepts

Examples:

- global selector matching is controlled by `SettingsStore.enableMatch`
- focus mode active state is driven by `focus_session` and active `focus_rule`s
- app/url blocker rules have their own `enabled` fields
- intercept overlays are configured through `intercept_config`
- lock windows are enforced through `constraint_config`, `is_locked`, and `lock_end_time` fields depending on domain
- auto re-enable can turn disabled content back on later

If you change disable behavior, audit all related entry points instead of patching only one UI switch.

### Browser support is partly data-driven

URL blocking depends on `BrowserConfig.urlBarId`. Built-in browser definitions are seeded in [`app/src/main/kotlin/li/songe/gkd/sdp/data/BrowserConfig.kt`](app/src/main/kotlin/li/songe/gkd/sdp/data/BrowserConfig.kt). If a browser cannot expose its address bar consistently, URL blocking will degrade.

### Accessibility code is timing-sensitive

`A11yRuleEngine` uses separate single-thread dispatchers for event ingestion, querying, and actions. A lot of logic exists to debounce noisy events, preserve ordering, and avoid stale node reads. Avoid “simple cleanup” refactors here unless you can prove behavior stays correct.

### Encoding matters

Gradle is configured with `-Dfile.encoding=UTF-8` in [`gradle.properties`](gradle.properties). Keep edits UTF-8. Some files already contain historical mojibake in comments or strings; do not spread that further when patching adjacent code.

## Build and Test

The authoritative verification environment is GitHub Actions with JDK 21. The repository is migrating the legacy workflows to `ci.yml` and `nightly.yml` in the infrastructure plan; until that migration is merged, use the workflow shown on the [Actions page](https://github.com/wskeei/gkd-SDP/actions). This project intentionally does not require Gradle to run locally; local checks can be limited to source inspection and `git diff --check`. Push the branch and inspect the Draft PR checks with `gh pr checks --watch`.

Formal GKD-SDP versions will be maintained separately from the upstream GKD base when the versioned-release phase lands. Until then, the legacy `app/build.gradle.kts` values remain the source of truth. Do not add new hard-coded version values to workflows or documentation; see [`docs/releasing.md`](docs/releasing.md) for the target procedure.

When a local Gradle run is explicitly appropriate for another task, the equivalent commands are:

```bash
./gradlew :app:assembleGkdDebug
./gradlew :app:testGkdDebugUnitTest
./gradlew :selector:jvmTest
```

For Windows PowerShell, use:

```powershell
.\gradlew.bat :app:assembleGkdDebug
.\gradlew.bat :app:testGkdDebugUnitTest
.\gradlew.bat :selector:jvmTest
```

Other useful tasks:

- `:app:assemblePlayDebug`
- `dependencyUpdates`
- `versionCatalogUpdate`

Signing is optional for local debug work. Release/play signing configs are loaded only when matching Gradle properties are present in the local environment.

## Suggested Reading Order

If you are new to the codebase, read in this order:

1. [`app/build.gradle.kts`](app/build.gradle.kts)
2. [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
3. [`app/src/main/kotlin/li/songe/gkd/sdp/App.kt`](app/src/main/kotlin/li/songe/gkd/sdp/App.kt)
4. [`app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt`](app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt)
5. [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yState.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yState.kt)
6. [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/SdpRuntimeFeatureCoordinator.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/SdpRuntimeFeatureCoordinator.kt)
7. [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yRuleEngine.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yRuleEngine.kt)
8. [`app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`](app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt)
9. [`app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`](app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt)
10. domain engines:
    - [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt)
    - [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/AppBlockerEngine.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/AppBlockerEngine.kt)
    - [`app/src/main/kotlin/li/songe/gkd/sdp/a11y/UrlBlockerEngine.kt`](app/src/main/kotlin/li/songe/gkd/sdp/a11y/UrlBlockerEngine.kt)

Then move to the view models for the feature you want to change.

## When Modifying Features

### If you change focus mode

Audit at least:

- `FocusModeEngine`
- `FocusSession`
- `FocusRule`
- `FocusModeVm`
- `FocusLockVm`
- `FocusOverlayService`

### If you change app or URL blocking

Audit at least:

- the Room entities and DAOs for that domain
- the engine object
- the related VM/page
- auto re-enable interactions
- lock state interactions

### If you change “turn off” behavior

Audit all disable entry points. In this codebase, “disable” is product-sensitive and may be guarded by quota, lock windows, or later automatic re-enable. Do not assume a single switch is authoritative.

## Agent Notes

For coding agents, the main trap is treating this as only a GKD selector project. It is not. The selector engine is only one subsystem. Many user-facing restrictions are implemented as separate Room-backed domain models plus overlay services, and they are merely coordinated through the same accessibility/runtime layer.

A second trap is over-trusting names. `gkd`, `subs`, `match`, `enable`, and `lock` have different meanings depending on the layer. Read the data model before changing behavior.

For any new or changed self-control feature, register the foreground-app/event handler in `SdpRuntimeFeatureCoordinator`, use `SelfControlOverlayLauncher` for overlays, and use the common HOME/BACK bridge for system navigation. This keeps accessibility and Automation/Shizuku mode behavior identical and makes failures visible in the runtime status card.
