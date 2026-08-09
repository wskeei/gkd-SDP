# Usage Guard Screenshot Mode Design

**Date:** 2026-08-09

## Problem

The active usage reminder is a `TYPE_APPLICATION_OVERLAY` window containing
the remaining time and the submitted request reason. The window currently uses
`FLAG_SECURE`. Android and some OEM screenshot implementations reject the whole
display capture whenever that secure overlay is visible, even though the
overlay itself is only `WRAP_CONTENT`.

The previous design accepted capture rejection as a valid privacy outcome. The
updated product requirement is different: the user must be able to capture the
third-party app without exposing the reminder text.

Android's public APIs do not provide a reliable pre-capture callback for a
service-owned overlay and do not expose the system-only `SKIP_SCREENSHOT` layer
flag. The implementation therefore uses an explicit, temporary hide action.

## Confirmed Product Decision

The user selected an explicit screenshot mode:

1. Tapping the countdown pill continues to open its full-screen control.
2. The control adds a full-width action labelled `隐藏 10 秒用于截图`.
3. Activating it removes the secure overlay window from `WindowManager`.
4. The approved usage record, expiration watcher, and remaining-time clock keep
   running while the window is absent.
5. The same overlay returns after ten seconds when the record is still current
   and unexpired.
6. The overlay does not return after expiry, service destruction, app handoff,
   runtime disconnection, or replacement by another record.
7. While mounted, the countdown and reason remain protected by `FLAG_SECURE`.
8. The focusable request form keeps its existing independent `FLAG_SECURE`.

## Interaction Design

The existing terminate confirmation becomes a small `使用控制` panel. It
contains:

- one concise explanation that hiding does not pause the usage session;
- one full-width outlined button, `隐藏 10 秒用于截图`;
- one short helper line, `隐藏期间倒计时继续，之后自动恢复。`;
- the existing return and terminate actions, renamed to `返回` and `终止使用`.

The screenshot action uses a Material button rather than an icon-only or
gesture-only affordance. This supplies native pressed feedback, an accessible
button role, a descriptive TalkBack label, and at least a 48 dp touch target.
No toast is shown after activation because a toast could itself appear in the
requested screenshot; disappearance of the overlay is the immediate feedback.

## Runtime Design

`UsageGuardCountdownOverlayService` owns both the active `ComposeView` and its
`WindowManager.LayoutParams`. It will explicitly distinguish these states:

- **Mounted pill:** the bounded secure window is visible and draggable.
- **Mounted control:** the same secure window is full-screen.
- **Temporarily hidden:** the view and params remain owned by the service, but
  the window is not attached to `WindowManager`.
- **Destroyed:** no restoration job or window remains.

The hide action resets the future layout to the compact pill, removes the
window, records the current app/record identity, and starts one lifecycle-bound
ten-second job. On wake-up it verifies identity and expiration before re-adding
the same view with the original secure flags. A stale job cannot restore a
different record.

If the initial mount or restoration is rejected, the service reports the
existing `countdown` mount-failure category, stops itself, and lets
`SdpRuntimeFeatureCoordinator` invalidate and recompute the current app. A
failed removal leaves the window mounted and does not pretend screenshot mode
started.

## Data, Privacy, and Compatibility

- `UsageGuardRecord` remains the only source of the reason and expiration.
- No Room entity, migration, schema, setting file, manifest permission, or new
  dependency is required.
- No sensitive reason text is added to logs, analytics, tests, or documents.
- Both `gkd` and `play` flavors use the same service behavior.
- Target apps that independently set `FLAG_SECURE` can still reject screenshots;
  this feature only removes GKD-SDP's own secure overlay from the display.

## Verification

Automated coverage will verify:

- the hide duration is exactly ten seconds;
- restoration is allowed only for the same unexpired record;
- expiry and identity changes suppress stale restoration;
- mounted countdown windows retain `FLAG_SECURE` and interaction flags;
- the service contains an explicit unmount, delayed restore, and accessible
  screenshot action.

Manual Android verification remains necessary for hardware-key and Quick
Settings screenshot behavior because capture handling is system/OEM-owned.
The required flow is: open a protected app, activate screenshot mode, capture
within ten seconds, verify the reminder is absent, then verify it returns with
the correct remaining time.
