# Usage Guard Screenshot Mode Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let a user temporarily remove the secure usage-countdown overlay so the foreground app can be captured without exposing the countdown or submitted reason.

**Architecture:** Keep `FLAG_SECURE` on the countdown window whenever it is mounted. Add a pure session/lease policy and a small service-owned state machine for an explicit ten-second hide action. The service removes its `ComposeView` from `WindowManager`, keeps the active record and expiry watcher untouched, and restores only the same unexpired session while the same runtime still owns the foreground app. Do not add system screenshot detection, a new permission, a setting, or a second reason source.

**Tech Stack:** Android 8.0+, Kotlin, Jetpack Compose Material 3, `LifecycleService`, coroutines, `WindowManager.TYPE_APPLICATION_OVERLAY`, JUnit 4.

---

### Task 1: Define the screenshot session policy with TDD

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicy.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicyTest.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayLeasePolicyTest.kt`

**Step 1: Write failing tests**

Define synthetic `UsageGuardCountdownOverlaySession` values and test that:

- `HIDE_DURATION_MS` is exactly `10_000L`;
- a matching, unexpired session may restore;
- expiry at `now`, invalid IDs, app changes, record replacement, and runtime-generation changes may not restore;
- only the exact current engine lease, foreground app, and runtime generation authorize restoration.

**Step 2: Run the focused tests and verify RED**

Run:

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayCapturePolicyTest' \
  --tests '*UsageGuardCountdownOverlayLeasePolicyTest'
```

Expected: compilation/test failure because the new policy and session types do not exist.

**Step 3: Implement the minimal pure policy**

Add immutable `UsageGuardCountdownOverlaySession` and `UsageGuardCountdownOverlayLease` values. Implement `shouldRestore(hidden, current, now)` as exact session equality plus `hidden.isValid(now)`. Implement `isLeaseActive(lease, session, foregroundAppId, currentRuntimeGeneration)` as exact lease equality plus foreground and generation equality.

**Step 4: Run the focused tests and verify GREEN**

Run the command from Step 2 and confirm all policy tests pass.

**Step 5: Commit the policy**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicy.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicyTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayLeasePolicyTest.kt
git commit -m "feat: define usage overlay screenshot policy"
```

### Task 2: Define service capture state transitions with TDD

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCaptureController.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCaptureControllerTest.kt`

**Step 1: Write failing controller tests**

Cover these transitions:

- a valid session without a view requests create-and-mount;
- an unchanged mounted session remains mounted;
- an unchanged hidden session remains hidden;
- a replacement session requests reset-and-mount;
- mount succeeds only after the WindowManager operation succeeds;
- a failed mount becomes terminal;
- hide succeeds only when the overlay was mounted;
- restore mounts only for the same current unexpired session and an active engine lease;
- expiry, revoked lease, replacement, and destruction suppress restoration.

**Step 2: Run the controller tests and verify RED**

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayCaptureControllerTest'
```

Expected: compilation failure because the controller does not exist.

**Step 3: Implement the pure controller**

Keep all Android window calls and coroutine scheduling in the service. The controller tracks only the current session, mounted state, and terminal state, and returns explicit start/restore actions. It must not log or store reason text.

**Step 4: Run the controller tests and verify GREEN**

Run the command from Step 2 and confirm all transition tests pass.

**Step 5: Commit the controller**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCaptureController.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCaptureControllerTest.kt
git commit -m "test: model usage overlay capture lifecycle"
```

### Task 3: Integrate leases into the engine and temporarily unmount the overlay

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayWindowFlagsTest.kt`

**Step 1: Write the failing service contract**

Assert that the service exposes an accessible `隐藏 10 秒用于截图` action, removes the mounted view before delaying, uses the policy before restoring, and keeps `FLAG_SECURE` in the mounted window contract. Also assert that a mount failure reports the existing `countdown` failure path.

**Step 2: Run the service contracts and verify RED**

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayScreenshotModeContractTest' \
  --tests '*UsageGuardCountdownOverlayWindowFlagsTest'
```

Expected: the new screenshot-mode contract fails while the existing secure-window contract remains green.

**Step 3: Add runtime leases in `UsageGuardEngine`**

Generate a monotonically increasing lease ID for each accepted countdown launch. Store the exact app, record, expiry, lease ID, and runtime-owner generation in an atomic lease. Pass the lease ID and generation to the service intent. Expose a read-only `canRestoreCountdownOverlay(session)` check that requires the stored lease, current foreground app, and current runtime generation to match. Clear or revoke the lease with existing stop, mount-failure, runtime-disconnect, and service-stop paths; do not change grant, expiry, or usage-record semantics.

**Step 4: Add service mount state and the explicit hide action**

Extract `WindowManager.addView` into a helper that marks the controller mounted only after success. Track one lifecycle-bound restore job. Wire the existing pill tap to a full-screen `使用控制` panel containing the accessible Material action `隐藏 10 秒用于截图` and the explanation that the countdown continues.

**Step 5: Remove and restore only the current window**

On the action, snapshot the current session, call `windowManager.removeView`, and mark the overlay hidden only after removal succeeds. Reset detached layout params to the compact pill state, cancel any previous restore job, and delay exactly `HIDE_DURATION_MS`. On wake-up, require the controller session, local service state, engine lease, foreground app, runtime generation, and expiry to match before re-adding the same view. Do not show a toast during the hidden interval. A failed removal leaves the secure window mounted; a failed restoration reports `onOverlayMountFailed("countdown", ...)` and stops the service.

**Step 6: Guard lifecycle and replacement paths**

Cancel restoration on service destruction and replacement sessions. Never call `updateViewLayout` while detached. Remove the view only when mounted. Preserve current drag, terminate, HOME/BACK, expiry, and active-record behavior.

**Step 7: Run the focused runtime tests and verify GREEN**

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlay*' \
  --tests '*UsageGuardEngine*' \
  --tests '*UsageGuardBlockingOverlayStateTest'
```

Expected: all focused tests pass, with the original secure flag retained while mounted.

**Step 8: Commit the runtime implementation**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayWindowFlagsTest.kt
git commit -m "fix: allow screenshots during active usage"
```

### Task 4: Update privacy and release verification documentation

**Files:**
- Modify: `README_DEV.md`
- Modify: `PRIVACY.md`
- Modify: `CHANGELOG.md` under `[Unreleased]`
- Modify: `docs/testing/release-smoke-checklist.md`

**Step 1: Document the behavior precisely**

State that the mounted countdown/reason overlay remains secure, and that users can explicitly hide it for ten seconds before taking a screenshot. State that the target app may still independently reject screenshots and that the feature cannot guarantee OEM behavior. Do not include real reasons, URLs, or device data.

**Step 2: Run documentation and static checks**

```bash
git diff --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

**Step 3: Commit the documentation**

```bash
git add README_DEV.md PRIVACY.md CHANGELOG.md docs/testing/release-smoke-checklist.md
git commit -m "docs: describe usage overlay screenshot mode"
```

### Task 5: Verify the branch before handoff

Run all available targeted tests, both flavor unit tests if available, `git diff --check`, and the relevant lint/build commands. Record any unavailable device/OEM verification explicitly. On a physical device, verify: request approval, secure mounted overlay, control-panel hide action, hardware screenshot within ten seconds with no readable countdown/reason, automatic restoration, expiry during hidden mode, app switch during hidden mode, and runtime owner handoff.

