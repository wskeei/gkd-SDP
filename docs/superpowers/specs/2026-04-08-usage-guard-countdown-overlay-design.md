# Usage Guard Countdown Overlay Design

Date: 2026-04-08
Topic: 数字自律 / 使用申请剩余时间悬浮窗
Status: Proposed

## Goal

Add a small semi-transparent countdown overlay for `使用申请` sessions so the user can see the remaining approved time while reading or using the target app.

The overlay must:

- show only while the approved protected app is in the foreground
- display remaining time with second-level precision
- start in the top-left corner for each newly entered session
- support dragging to another position during the current session
- keep visual footprint small enough to avoid harming readability

## Confirmed Requirements

From the discussion, the feature should behave as follows:

- It appears only after the user has completed the `使用申请` form and the request has been granted.
- It is shown only when the currently foreground app is the same protected app as the active `UsageGuardRecord`.
- Leaving the app or returning to the launcher hides the overlay.
- Each new session resets the overlay position to the top-left corner.
- The overlay text shows only the countdown, with no label such as `剩余`.
- The smallest display unit is seconds.
- The overlay should interfere with reading and taps as little as possible.

## Non-Goals

This change does not add:

- persistent drag position storage
- per-app custom overlay position
- settings toggles for countdown style, color, or size
- countdown UI inside the request form or timeout screen
- changes to `UsageGuard` policy, approval validation, or record schema

## Recommended Approach

Use a dedicated overlay service: `UsageGuardCountdownOverlayService`.

Recommendation reasons:

- It matches the existing `UsageGuardRequestOverlayService` and `UsageGuardTimeoutOverlayService` structure.
- It keeps `UsageGuardEngine` focused on session state instead of embedding full overlay UI lifecycle management inside the engine.
- It avoids coupling the countdown UI to either the request overlay or the timeout overlay, both of which have different purposes and full-screen interaction models.

Rejected alternatives:

- Put the countdown UI directly inside `UsageGuardEngine`: fewer files, but the engine becomes responsible for both runtime policy and view/window lifecycle.
- Make the countdown window fully click-through while also draggable: this is not a good fit for a reliable Android overlay interaction model. A fully non-touchable window cannot be directly dragged, so this creates hidden interaction modes and unnecessary complexity.

## Architecture

### Runtime ownership

`UsageGuardEngine` remains the single source of truth for whether a `UsageGuard` session is active and whether the protected app is currently foregrounded.

`UsageGuardCountdownOverlayService` becomes a thin UI layer that:

- receives the current app/session timing snapshot
- displays a small overlay window
- updates the visible countdown once per second
- allows drag-to-move within the current overlay lifetime
- closes itself when told to stop or when the session is no longer displayable

### Source of timing truth

The countdown must derive from the active `UsageGuardRecord.expiresAt`.

No duplicate countdown state should be persisted. The displayed value is calculated from:

`remainingMs = max(0, expiresAt - System.currentTimeMillis())`

Formatting rules:

- below 1 hour: `MM:SS`
- 1 hour and above: `H:MM:SS`

Examples:

- `09:58`
- `59:59`
- `1:02:11`

### Window strategy

The countdown overlay should use its own `TYPE_APPLICATION_OVERLAY` window with:

- `WRAP_CONTENT` width and height
- initial gravity/coordinates anchored to the top-left safe margin
- a semi-transparent background
- high enough z-order to stay visible above the target app

The window should not be full-screen and should not block the rest of the app.

## Component Changes

### 1. New service

Create `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt`.

Responsibilities:

- build and own the small overlay window
- render countdown text only
- refresh once per second
- handle dragging by updating `WindowManager.LayoutParams.x/y`
- stop itself when asked by the engine or when the session is no longer valid

### 2. UsageGuardEngine changes

Modify `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`.

New responsibilities:

- start the countdown overlay after `onRequestGranted(appId)` successfully confirms an active record
- keep the overlay shown only while the active foreground app matches the session app
- stop the overlay when:
  - the foreground app changes away from the active protected app
  - the active record is closed
  - the request overlay is shown
  - the timeout overlay is shown
  - the session expires

Important boundary:

`UsageGuardEngine` decides visibility. The countdown service does not independently decide whether the session should still exist, except for a defensive self-stop if it detects invalid input or zero remaining time at launch.

### 3. No storage changes

No Room schema or `SettingsStore` changes are needed.

The existing `UsageGuardRecord` fields already provide all required timing information:

- `grantedAt`
- `expiresAt`
- `endedAt`
- `endReason`

## Display and Interaction Design

### Visual design

The overlay should remain intentionally quiet:

- small rounded rectangle or capsule
- semi-transparent dark background
- high-contrast light text
- compact horizontal and vertical padding
- no icon
- no app name
- no label text

The visual purpose is reference, not emphasis.

### Default placement

For every newly shown countdown overlay instance:

- initialize near the top-left corner
- include a small margin from the status bar / display edge
- do not restore a previous dragged position

This applies even if the user dragged the previous overlay somewhere else.

### Drag behavior

Dragging is supported directly on the small overlay surface.

Implementation rule:

- treat the whole pill as the drag handle
- update overlay `x/y` continuously during drag
- clamp movement to keep the overlay at least partially visible on screen

Because the overlay must be draggable, it cannot be fully click-through. The design compromise is:

- keep the overlay physically small
- avoid any extra invisible touch area
- accept touch interception only on the overlay itself

This is the closest practical match to the request for `尽量不拦截点`.

## Visibility Rules

The countdown overlay is visible only when all of the following are true:

- `UsageGuard` is enabled
- there is an active `UsageGuardRecord` for the current protected app
- `endedAt == 0`
- `expiresAt > now`
- the currently foreground app equals `record.appId`
- request overlay is not currently displayed for that app
- timeout overlay is not currently displayed for that app

The countdown overlay must be hidden immediately when any of the following occurs:

- the user leaves the protected app
- the session expires
- the record is replaced by a new approval
- the session is closed for strict-mode leave-app behavior
- the timeout overlay is launched
- the app/process/service is destroyed

## Data Flow

### Happy path

1. User submits `使用申请`.
2. `UsageGuardRequestOverlayService` writes a new active `UsageGuardRecord`.
3. `UsageGuardEngine.onRequestGranted(appId)` loads the active record and schedules expiry watch.
4. `UsageGuardEngine` starts `UsageGuardCountdownOverlayService` with the session identity and `expiresAt`.
5. Countdown service shows a small overlay in the top-left corner.
6. Countdown UI recomputes the visible text every second.
7. If the user drags it, only in-memory window coordinates change.
8. When time reaches zero, the engine closes the record and shows `UsageGuardTimeoutOverlayService`.
9. Countdown overlay is stopped before or as part of timeout presentation.

### Foreground change path

1. `UsageGuardEngine.onAppChanged(nextApp)` runs.
2. If `nextApp != activeRecord.appId`, the countdown overlay is stopped.
3. If strict mode applies, the record may also be closed according to existing logic.
4. If the user returns later under resumable mode and the record is still active, the countdown overlay is started again at the default top-left position.

## Error Handling

The countdown overlay should fail closed, not linger.

If the service starts with any of the following, it should immediately stop itself:

- blank `appId`
- missing or non-positive `expiresAt`
- already expired session

If window creation throws, the session itself should continue to function; only the countdown hint is missing. This avoids breaking the core `UsageGuard` workflow because of an auxiliary UI failure.

## Testing Scope

### Unit-level logic

Add focused tests for pure formatting logic if extracted into a helper.

Suggested cases:

- `59s -> 00:59`
- `10m 5s -> 10:05`
- `59m 59s -> 59:59`
- `1h 0m 0s -> 1:00:00`
- negative remaining time clamps to `00:00`

### Manual verification

Verify these flows on device:

1. Open a protected app and submit a short approval.
2. Confirm a small semi-transparent countdown appears in the top-left corner.
3. Confirm it updates every second.
4. Drag it to another position and confirm it follows the finger smoothly.
5. Confirm only the pill itself intercepts touch; surrounding content remains tappable.
6. Leave the app and confirm the overlay disappears immediately.
7. Return to the same app before expiry in resumable mode and confirm the overlay reappears at the top-left corner.
8. Repeat in strict mode and confirm leaving the app ends the session and no countdown returns.
9. Let the session expire and confirm the countdown disappears before the timeout overlay takes over.
10. Start a new approval after dragging the old overlay and confirm the new countdown starts again in the top-left corner.

## Risks and Mitigations

### Risk: overlay interferes with content taps

Mitigation:

- keep the overlay visually and physically small
- avoid full-width or expanded hit targets
- do not add extra controls inside the overlay

### Risk: countdown and engine expiry drift

Mitigation:

- derive display strictly from `expiresAt`
- let `UsageGuardEngine` remain authoritative for actual expiry and record closure

### Risk: overlay survives after app switch

Mitigation:

- stop the service from `UsageGuardEngine.onAppChanged`
- also add defensive stop logic in the service on invalid session launch state

## Implementation Plan Summary

1. Add `UsageGuardCountdownOverlayService` with compact overlay UI, countdown formatting, and drag support.
2. Extend `UsageGuardEngine` to start and stop the countdown overlay according to active session visibility rules.
3. Add focused tests for countdown formatting if the formatter is extracted.
4. Run manual device verification for overlay timing, drag behavior, visibility transitions, and expiry handoff.
