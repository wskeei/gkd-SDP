# Usage Guard Screenshot Mode Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let users temporarily remove the secure usage-countdown overlay for ten seconds so the foreground app can be screenshotted without exposing the countdown or request reason.

**Architecture:** Keep `FLAG_SECURE` whenever the countdown window is mounted. Add a pure capture policy for the ten-second duration and stale-restore checks, then let `UsageGuardCountdownOverlayService` temporarily remove and later re-add its existing `ComposeView` while the service, active record, and engine expiry watch continue running.

**Tech Stack:** Android 8.0+, Kotlin, Jetpack Compose Material 3, `LifecycleService`, coroutines, `WindowManager.TYPE_APPLICATION_OVERLAY`, JUnit 4.

---

### Task 1: Define the temporary-hide policy with TDD

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicy.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicyTest.kt`

**Step 1: Write the failing policy tests**

Use `@superpowers:test-driven-development`. Cover the fixed ten-second duration,
the same unexpired record, exact expiry, expired records, invalid identities,
and an app/record replacement:

```kotlin
assertEquals(10_000L, UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS)
assertTrue(
    UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
        hiddenAppId = "com.example.target",
        hiddenRecordId = 7L,
        currentAppId = "com.example.target",
        currentRecordId = 7L,
        expiresAt = 20_001L,
        now = 20_000L,
    ),
)
assertFalse(/* expiresAt == now */)
assertFalse(/* currentRecordId differs */)
```

Use only synthetic package names and IDs.

**Step 2: Run the focused test and verify RED**

Run:

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayCapturePolicyTest'
```

Expected: FAIL because `UsageGuardCountdownOverlayCapturePolicy` does not yet
exist. If the host has no JDK, record the environment failure and retain the
test for CI rather than weakening it.

**Step 3: Implement the minimal pure policy**

```kotlin
object UsageGuardCountdownOverlayCapturePolicy {
    const val HIDE_DURATION_MS = 10_000L

    fun shouldRestore(
        hiddenAppId: String,
        hiddenRecordId: Long,
        currentAppId: String,
        currentRecordId: Long,
        expiresAt: Long,
        now: Long,
    ): Boolean {
        return hiddenAppId.isNotBlank() &&
            hiddenRecordId > 0L &&
            hiddenAppId == currentAppId &&
            hiddenRecordId == currentRecordId &&
            expiresAt > now
    }
}
```

**Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: PASS.

**Step 5: Commit the policy increment**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicy.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayCapturePolicyTest.kt
git commit -m 'feat: define usage overlay screenshot mode'
```

### Task 2: Prove the service and UI contract before implementation

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayWindowFlagsTest.kt`

**Step 1: Write a failing source contract**

Read `UsageGuardCountdownOverlayService.kt` using the repository's existing
workspace-independent `sourceFile()` helper pattern. Assert that the source:

- exposes the exact text `隐藏 10 秒用于截图`;
- wires `onHideForScreenshot` from the Material button to the service callback;
- tracks whether the window is mounted;
- removes the overlay view before scheduling restoration;
- delays with `HIDE_DURATION_MS`;
- calls the pure `shouldRestore` policy before re-mounting;
- keeps the existing `FLAG_SECURE` window contract.

**Step 2: Run the service contracts and verify RED**

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayScreenshotModeContractTest' \
  --tests '*UsageGuardCountdownOverlayWindowFlagsTest'
```

Expected: the new screenshot-mode contract FAILS because no action or temporary
unmount path exists; the pre-existing secure-flags contract remains green.

**Step 3: Keep the failing tests unchanged**

Do not remove `FLAG_SECURE` from the countdown window and do not weaken the
existing assertion to make screenshots pass.

### Task 3: Implement lifecycle-safe temporary unmount and restore

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt`

**Step 1: Add explicit mount state and a lifecycle-bound restoration job**

Add:

```kotlin
private var overlayMounted = false
private var restoreOverlayJob: Job? = null
```

Extract the existing `addView` call into one `mountOverlayView()` helper that
sets `overlayMounted = true` only after `WindowManager.addView` succeeds.

**Step 2: Implement the screenshot action**

The service callback must:

1. Return unless the current view and params are mounted.
2. Capture the current `appId` and `recordId`.
3. Call `windowManager.removeView(overlayView)` and set
   `overlayMounted = false` only after success.
4. Reset the detached params to compact `WRAP_CONTENT` at the normal safe
   position and close the full-screen control state.
5. Launch exactly one `lifecycleScope` job that delays for
   `UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS`.
6. Re-check app identity, record identity, and `expiresAt` with the pure policy.
7. Re-add the same view only when the policy returns true.
8. Stop the service instead of restoring when the same record has expired.

A removal failure must leave the existing secure window mounted. A restoration
failure must report `UsageGuardEngine.onOverlayMountFailed("countdown", appId)`
and stop the service so the shared runtime can recompute.

**Step 3: Guard all layout mutations by mount state**

Do not call `WindowManager.updateViewLayout` while temporarily hidden. A new
record reset must cancel the stale restore job and mount the new compact state;
service destruction must cancel the job and remove the window only when it is
actually mounted.

**Step 4: Add the accessible Material action**

Use `@ui-ux-pro-max`. Change the control title to `使用控制`, retain the return
and terminate actions, and add:

```kotlin
OutlinedButton(
    onClick = onHideForScreenshot,
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
) {
    Text("隐藏 10 秒用于截图")
}
Text("隐藏期间倒计时继续，之后自动恢复。")
```

Use Material theme colors and native ripple feedback. Do not add an emoji,
icon-only control, toast, continuous animation, or a new gesture conflict with
the pill's existing tap and drag handlers.

**Step 5: Run the policy and service contracts and verify GREEN**

```bash
bash ./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardCountdownOverlayCapturePolicyTest' \
  --tests '*UsageGuardCountdownOverlayScreenshotModeContractTest' \
  --tests '*UsageGuardCountdownOverlayWindowFlagsTest' \
  --tests '*UsageGuardCountdownOverlayLayoutPolicyTest' \
  --tests '*UsageGuardCountdownOverlayPolicyTest'
```

Expected: PASS.

**Step 6: Commit the service increment**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt
git commit -m 'fix: allow temporary usage overlay hiding'
```

### Task 4: Update user-visible and maintenance documentation

**Files:**
- Modify: `README_DEV.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/testing/release-smoke-checklist.md`

**Step 1: Correct the runtime contract**

Document that the countdown window remains secure while visible, the explicit
action removes it for ten seconds without pausing usage, and only GKD-SDP's own
overlay is controlled. Remove the obsolete implication that capture rejection
is the only accepted outcome for the active countdown.

**Step 2: Add the Unreleased fix**

Add a concise `[Unreleased]` entry explaining that screenshots are restored
through explicit ten-second hiding while reminder privacy remains intact when
mounted.

**Step 3: Extend the release smoke checklist**

Require hardware-key and Quick Settings capture during the hide window, correct
remaining time after restoration, expiry during hiding, app switching, and a
target app that independently rejects screenshots. Do not claim OEM-wide
behavior from automation.

**Step 4: Commit the documentation increment**

```bash
git add README_DEV.md CHANGELOG.md docs/testing/release-smoke-checklist.md
git commit -m 'docs: explain usage overlay screenshot mode'
```

### Task 5: Verify the complete branch

**Files:**
- No new files unless verification exposes a defect.

**Step 1: Run source and repository hygiene checks**

```bash
git diff --check origin/main...HEAD
git status --short --branch
git diff --name-only origin/main...HEAD
```

Expected: no Room schema, database, manifest, settings, dependency, or request
form changes.

**Step 2: Run the complete unit suite**

```bash
bash ./gradlew :app:testGkdDebugUnitTest
```

Expected: PASS.

**Step 3: Compile both debug flavors**

```bash
bash ./gradlew :app:assembleGkdDebug :app:assemblePlayDebug
```

Expected: PASS.

**Step 4: Review the UI checklist**

Use `@ui-ux-pro-max` to verify the 48 dp action target, descriptive TalkBack
text, large-font wrapping, landscape layout, dark/light theme tokens, safe
areas, and absence of conflicting gestures or decorative animation.

**Step 5: Request code review**

Use `@superpowers:requesting-code-review` against `origin/main...HEAD`. Resolve
all Critical and Important findings, then re-run the affected checks.

**Step 6: Perform the final evidence gate**

Use `@superpowers:verification-before-completion`. Record every command actually
run, its exit status, and any unavailable Android-device or local-JDK checks.
Do not claim physical screenshot behavior without device evidence.
