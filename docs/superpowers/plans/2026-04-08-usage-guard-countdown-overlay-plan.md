# Usage Guard Countdown Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small draggable countdown overlay for approved `使用申请` sessions that shows second-level remaining time only while the protected app is foregrounded.

**Architecture:** Keep `UsageGuardEngine` as the authority for session state and foreground gating. Add one dedicated overlay service for the pill UI and one small pure helper for countdown text formatting plus display gating so the risky logic is unit-tested before touching runtime code.

**Tech Stack:** Kotlin, Android `LifecycleService`, `WindowManager` overlays, Jetpack Compose Material3, Kotlin coroutines, existing accessibility foreground-app pipeline, JUnit4 unit tests.

---

## File Map

**Create:**
- `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicy.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicyTest.kt`

**Modify:**
- `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`

**Responsibilities:**
- `UsageGuardCountdownOverlayPolicy.kt`: pure countdown formatting and visibility gating rules.
- `UsageGuardCountdownOverlayPolicyTest.kt`: locks down `MM:SS` / `H:MM:SS` formatting and the foreground-only visibility contract.
- `UsageGuardCountdownOverlayService.kt`: owns the small semi-transparent `TYPE_APPLICATION_OVERLAY`, one-second refresh loop, drag handling, and reset-to-top-left startup placement.
- `UsageGuardEngine.kt`: starts and stops the countdown overlay alongside existing request and timeout overlay transitions.

**No changes planned:**
- `UsageGuardRequestOverlayService.kt`
- `UsageGuardTimeoutOverlayService.kt`
- Room entities / `SettingsStore`
- `A11yFeat.kt` registration

The existing code already calls `UsageGuardEngine.onAppChanged(...)`, already records `expiresAt`, and already routes request and timeout overlays through the engine.

---

### Task 1: Lock countdown formatting and visibility rules with pure tests first

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicy.kt`

- [ ] **Step 1: Write the failing formatter and visibility contract tests**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayPolicyTest {
    @Test
    fun formatRemainingTextUsesMinuteSecondLayoutBelowOneHour() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 598_000L,
            now = 0L,
        )

        assertEquals("09:58", text)
    }

    @Test
    fun formatRemainingTextUsesHourLayoutAtOneHourOrMore() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 3_731_000L,
            now = 0L,
        )

        assertEquals("1:02:11", text)
    }

    @Test
    fun formatRemainingTextClampsExpiredSessionsToZero() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 1_000L,
            now = 1_500L,
        )

        assertEquals("00:00", text)
    }

    @Test
    fun shouldDisplayRequiresForegroundMatchAndNoCompetingOverlay() {
        val record = UsageGuardRecord(
            id = 7L,
            appId = "com.example.reader",
            appName = "Reader",
            tagNames = listOf("查资料"),
            reasonText = "读一篇文章",
            requestedDurationMinutes = 10,
            requestedAt = 0L,
            grantedAt = 0L,
            expiresAt = 600_000L,
        )

        assertTrue(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = "com.example.reader",
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.other",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
    }
}
```

- [ ] **Step 2: Run the new test target to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardCountdownOverlayPolicyTest"
```

Expected: FAIL because `UsageGuardCountdownOverlayPolicy` does not exist yet.

- [ ] **Step 3: Implement the pure countdown helper**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import java.util.Locale

object UsageGuardCountdownOverlayPolicy {
    fun formatRemainingText(
        expiresAt: Long,
        now: Long = System.currentTimeMillis(),
    ): String {
        val remainingSeconds = ((expiresAt - now).coerceAtLeast(0L) + 999L) / 1000L
        val hours = remainingSeconds / 3600L
        val minutes = (remainingSeconds % 3600L) / 60L
        val seconds = remainingSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun shouldDisplay(
        activeRecord: UsageGuardRecord?,
        foregroundAppId: String,
        requestOverlayAppId: String?,
        timeoutOverlayAppId: String?,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (activeRecord == null) return false
        if (foregroundAppId != activeRecord.appId) return false
        if (activeRecord.endedAt != 0L) return false
        if (activeRecord.expiresAt <= now) return false
        if (requestOverlayAppId == activeRecord.appId) return false
        if (timeoutOverlayAppId == activeRecord.appId) return false
        return true
    }
}
```

- [ ] **Step 4: Re-run the test target to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardCountdownOverlayPolicyTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the pure helper before touching runtime code**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardCountdownOverlayPolicyTest.kt
git commit -m "test: add usage guard countdown overlay policy"
```

---

### Task 2: Add the standalone countdown overlay service with drag support

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt`

- [ ] **Step 1: Add the service shell and intent contract**

```kotlin
package li.songe.gkd.sdp.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import li.songe.gkd.sdp.a11y.UsageGuardEngine

class UsageGuardCountdownOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var recordId: Long = 0L
    private var expiresAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }
}
```

- [ ] **Step 2: Read extras, reject invalid launches, and start a small top-left overlay window**

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    if (view != null) return START_NOT_STICKY

    appId = intent?.getStringExtra("appId").orEmpty()
    recordId = intent?.getLongExtra("recordId", 0L) ?: 0L
    expiresAt = intent?.getLongExtra("expiresAt", 0L) ?: 0L

    if (appId.isBlank() || recordId <= 0L || expiresAt <= System.currentTimeMillis()) {
        stopSelf()
        return START_NOT_STICKY
    }

    val marginPx = (resources.displayMetrics.density * 12).toInt()
    layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        x = marginPx
        y = marginPx
    }

    showOverlay()
    return START_NOT_STICKY
}
```

- [ ] **Step 3: Render the compact countdown pill and one-second refresh loop**

```kotlin
private fun showOverlay() {
    if (view != null) return

    view = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@UsageGuardCountdownOverlayService)
        setViewTreeSavedStateRegistryOwner(this@UsageGuardCountdownOverlayService)
        setContent {
            AppTheme {
                UsageGuardCountdownOverlayContent(
                    expiresAt = expiresAt,
                    onExpired = { stopSelf() },
                    onDrag = ::updatePosition,
                )
            }
        }
    }

    windowManager.addView(view, layoutParams)
}

@Composable
private fun UsageGuardCountdownOverlayContent(
    expiresAt: Long,
    onExpired: () -> Unit,
    onDrag: (dx: Int, dy: Int) -> Unit,
) {
    var now by remember(expiresAt) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) {
                onExpired()
                break
            }
            delay(1_000L)
        }
    }

    Surface(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                }
            },
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.45f),
    ) {
        Text(
            text = UsageGuardCountdownOverlayPolicy.formatRemainingText(expiresAt, now),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
```

- [ ] **Step 4: Clamp drag movement and clear engine state on destroy**

```kotlin
private fun updatePosition(dx: Int, dy: Int) {
    val params = layoutParams ?: return
    val contentView = view ?: return
    val screenWidth = resources.displayMetrics.widthPixels
    val screenHeight = resources.displayMetrics.heightPixels
    val maxX = (screenWidth - contentView.width).coerceAtLeast(0)
    val maxY = (screenHeight - contentView.height).coerceAtLeast(0)

    params.x = (params.x + dx).coerceIn(0, maxX)
    params.y = (params.y + dy).coerceIn(0, maxY)
    windowManager.updateViewLayout(contentView, params)
}

override fun onDestroy() {
    super.onDestroy()
    view?.let { windowManager.removeView(it) }
    view = null
    layoutParams = null
    UsageGuardEngine.onCountdownOverlayStopped(appId.ifBlank { null })
}
```

- [ ] **Step 5: Run a compile-focused verification**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardCountdownOverlayPolicyTest"
```

Expected: PASS and no unresolved Compose or overlay-service symbols during compile.

- [ ] **Step 6: Commit the standalone overlay**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt
git commit -m "feat: add usage guard countdown overlay service"
```

---

### Task 3: Extend UsageGuardEngine to own countdown visibility

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`

- [ ] **Step 1: Add engine state for the countdown overlay instance**

```kotlin
private var countdownOverlayAppId: String? = null
private var countdownOverlayRecordId: Long? = null
private var countdownOverlayExpiresAt: Long = 0L
```

And add the clear callback:

```kotlin
fun onCountdownOverlayStopped(appId: String?) {
    appScope.launch {
        stateMutex.withLock {
            if (countdownOverlayAppId == appId) {
                countdownOverlayAppId = null
                countdownOverlayRecordId = null
                countdownOverlayExpiresAt = 0L
            }
        }
    }
}
```

- [ ] **Step 2: Add start and stop helpers for the countdown service**

```kotlin
private fun showCountdownOverlay(service: A11yService, record: UsageGuardRecord) {
    if (
        countdownOverlayAppId == record.appId &&
        countdownOverlayRecordId == record.id &&
        countdownOverlayExpiresAt == record.expiresAt
    ) {
        return
    }

    stopCountdownOverlay(service)
    countdownOverlayAppId = record.appId
    countdownOverlayRecordId = record.id
    countdownOverlayExpiresAt = record.expiresAt
    service.startService(Intent(service, UsageGuardCountdownOverlayService::class.java).apply {
        putExtra("appId", record.appId)
        putExtra("recordId", record.id)
        putExtra("expiresAt", record.expiresAt)
    })
}

private fun stopCountdownOverlay(
    service: A11yService? = A11yService.instance,
    appId: String? = null,
) {
    if (appId != null && countdownOverlayAppId != appId) return
    service?.stopService(Intent(service, UsageGuardCountdownOverlayService::class.java))
    countdownOverlayAppId = null
    countdownOverlayRecordId = null
    countdownOverlayExpiresAt = 0L
}
```

- [ ] **Step 3: Stop the pill immediately when the user leaves the protected app**

At the start of `handleAppChanged(...)`, after closing the previous strict-mode session, add:

```kotlin
if (countdownOverlayAppId != null && countdownOverlayAppId != packageName) {
    stopCountdownOverlay(service, countdownOverlayAppId)
}
```

Also stop it in every early-return branch that means the current foreground app should not show the hint:

```kotlin
if (shouldSkipApp(packageName)) {
    stopCountdownOverlay(service)
    lastProtectedAppId = null
    return
}
```

Use the same `stopCountdownOverlay(service)` call in the `FocusModeEngine` block, the `AppBlockerEngine.shouldBlock(...)` block, and the `!shouldProtect` block.

- [ ] **Step 4: Start the pill only for active foreground sessions and stop it before competing overlays**

When no active record exists:

```kotlin
cancelExpiryWatch(packageName)
stopCountdownOverlay(service, packageName)
showRequestOverlay(service, packageName, profile, settings.usageGuardMinReasonLength)
requestOverlayAppId = packageName
return
```

When the record has already expired:

```kotlin
cancelExpiryWatch(packageName)
stopCountdownOverlay(service, packageName)
DbSet.usageGuardRecordDao.closeRecord(
    id = activeRecord.id,
    endedAt = now,
    endReason = UsageGuardRecord.END_REASON_EXPIRED,
)
showTimeoutOverlay(service, packageName, activeRecord.id, activeRecord.reasonText)
timeoutOverlayAppId = packageName
return
```

When the record is still active:

```kotlin
scheduleExpiryWatch(activeRecord)
if (
    UsageGuardCountdownOverlayPolicy.shouldDisplay(
        activeRecord = activeRecord,
        foregroundAppId = packageName,
        requestOverlayAppId = requestOverlayAppId,
        timeoutOverlayAppId = timeoutOverlayAppId,
        now = now,
    )
) {
    showCountdownOverlay(service, activeRecord)
} else {
    stopCountdownOverlay(service, packageName)
}
```

- [ ] **Step 5: Start the pill after approval and stop it during expiry handoff**

Update `onRequestGranted(appId)`:

```kotlin
fun onRequestGranted(appId: String) {
    appScope.launch(Dispatchers.IO) {
        stateMutex.withLock {
            requestOverlayAppId = null
            lastProtectedAppId = appId
            val record = DbSet.usageGuardRecordDao.getActiveRecord(appId) ?: return@withLock
            scheduleExpiryWatch(record)
            if (topActivityFlow.value.appId == appId) {
                A11yService.instance?.let { showCountdownOverlay(it, record) }
            }
        }
    }
}
```

And inside the expiry watcher, before launching the timeout overlay:

```kotlin
cancelExpiryWatch(record.appId)
A11yService.instance?.let { service ->
    stopCountdownOverlay(service, record.appId)
    showTimeoutOverlay(service, record.appId, activeRecord.id, activeRecord.reasonText)
    timeoutOverlayAppId = record.appId
}
```

- [ ] **Step 6: Run focused tests after runtime wiring**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardCountdownOverlayPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuard*Test"
```

Expected: both commands PASS.

- [ ] **Step 7: Commit the runtime wiring**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt
git commit -m "feat: wire usage guard countdown overlay"
```

---

### Task 4: Full verification and acceptance for countdown behavior

**Files:**
- No new files

- [ ] **Step 1: Run the exact automated checks**

```powershell
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardCountdownOverlayPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuard*Test"
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manual acceptance for default placement and ticking**

Check:
1. Open a protected app and complete `使用申请` with a short duration.
2. Confirm a small semi-transparent pill appears in the top-left corner.
3. Confirm the text is countdown-only, with no `剩余` label.
4. Confirm the value updates every second.

- [ ] **Step 3: Manual acceptance for drag behavior and low interference**

Check:
1. Drag the pill to another location.
2. Confirm it follows the finger without jumping.
3. Confirm only the pill area intercepts touch.
4. Confirm content outside the pill remains tappable.

- [ ] **Step 4: Manual acceptance for foreground-only visibility**

Check:
1. Leave the approved protected app.
2. Confirm the pill disappears immediately.
3. Return to the same app before expiry in resumable mode.
4. Confirm the pill reappears and starts again in the top-left corner.

- [ ] **Step 5: Manual acceptance for strict mode and expiry handoff**

Check:
1. Approve an app under strict mode.
2. Leave the app.
3. Confirm the session ends and no countdown returns on re-entry.
4. Approve again and wait for expiry.
5. Confirm the countdown pill disappears before the timeout overlay appears.

- [ ] **Step 6: Report exact results**

Do not mark the feature complete until you have:
- quoted the exact Gradle command results
- stated whether the manual device checks in Steps 2-5 were run
- listed any checks that were skipped or only partially verified
