# Auto Re-Enable Guard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a non-disableable auto re-enable guard that periodically turns ON all user-disableable items in subscription/app-blocker/url-blocker domains, with interval configurable to `0..240` minutes (hard cap) and interval changes limited to once every 72 hours.

**Architecture:** Add a pure policy layer for interval/cooldown validation, a background enforcer loop started from app process init, and DAO-level bulk re-enable operations for all target tables. Expose only interval editing UI (no OFF switch). Enforcer runs periodically: `interval=0` uses aggressive polling; otherwise uses configured minutes.

**Tech Stack:** Kotlin, Room, Kotlin Coroutines, Jetpack Compose, existing `storeFlow` persistence.

---

### Task 1: Policy Rules (TDD first)

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicy.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicyTest.kt`

**Step 1: Write failing tests for interval bounds and 72h cooldown**

```kotlin
class AutoReenablePolicyTest {
    @Test fun `normalize interval clamps to 0 and 240`() {
        assertEquals(0, AutoReenablePolicy.normalizeIntervalMinutes(-1))
        assertEquals(240, AutoReenablePolicy.normalizeIntervalMinutes(999))
    }

    @Test fun `cooldown blocks edit before 72h`() {
        val now = 1_000_000L
        val last = now - (71L * 60 * 60 * 1000)
        assertFalse(AutoReenablePolicy.canChangeInterval(last, now))
    }

    @Test fun `cooldown allows edit at 72h or later`() {
        val now = 1_000_000L
        val last = now - (72L * 60 * 60 * 1000)
        assertTrue(AutoReenablePolicy.canChangeInterval(last, now))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AutoReenablePolicyTest"`
Expected: FAIL (class not found).

**Step 3: Write minimal policy implementation**

```kotlin
object AutoReenablePolicy {
    const val MAX_INTERVAL_MINUTES = 240
    const val CHANGE_COOLDOWN_MS = 72L * 60 * 60 * 1000

    fun normalizeIntervalMinutes(value: Int): Int = value.coerceIn(0, MAX_INTERVAL_MINUTES)

    fun canChangeInterval(lastChangedAt: Long, now: Long): Boolean {
        if (lastChangedAt <= 0L) return true
        return now - lastChangedAt >= CHANGE_COOLDOWN_MS
    }

    fun nextEnforceDelayMs(intervalMinutes: Int): Long {
        val normalized = normalizeIntervalMinutes(intervalMinutes)
        return if (normalized == 0) 15_000L else normalized * 60_000L
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AutoReenablePolicyTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicyTest.kt
git commit -m "feat: add auto re-enable policy and tests"
```

### Task 2: Persist Interval + Cooldown Metadata

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Step 1: Write failing VM test for edit cooldown behavior**

```kotlin
@Test fun `update interval rejected during cooldown`() {
    // given last change 1h ago
    // when request new interval
    // then store not updated and remaining cooldown returned
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FocusLockVm*"`
Expected: FAIL (test or helper missing).

**Step 3: Add settings fields with safe defaults**

```kotlin
val autoReenableIntervalMinutes: Int = 0,
val autoReenableIntervalChangedAt: Long = 0L,
```

**Step 4: Add VM method to update interval with hard rules**

- Clamp to `0..240`.
- If cooldown active, reject and toast remaining time.
- If allowed, update both `autoReenableIntervalMinutes` and `autoReenableIntervalChangedAt=now`.

**Step 5: Add read-only UI entry (no disable switch)**

- Show current interval.
- Show next editable time when cooldown active.
- Entry opens interval picker dialog only.

**Step 6: Re-run VM test**

Run: `./gradlew :app:testDebugUnitTest --tests "*FocusLockVm*"`
Expected: PASS.

**Step 7: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt
git commit -m "feat: add interval config and 72h edit cooldown"
```

### Task 3: DAO Bulk Re-Enable Operations

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/SubsItem.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/AppGroup.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/BlockTimeRule.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UrlRuleGroup.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UrlBlockRule.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UrlTimeRule.kt`

**Step 1: Write failing tests for re-enable SQL paths (at least one DAO integration test)**

```kotlin
@Test fun `enableAllDisabled updates disabled rows only`() {
    // insert enabled+disabled rows
    // run enableAllDisabled
    // assert all enabled
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*Dao*"`
Expected: FAIL.

**Step 3: Add DAO methods per table**

```kotlin
@Query("UPDATE subs_item SET enable = 1 WHERE enable = 0")
suspend fun enableAllDisabled(): Int
```

Apply equivalent methods for:
- `app_group.enabled`
- `block_time_rule.enabled`
- `url_rule_group.enabled`
- `url_block_rule.enabled`
- `url_time_rule.enabled`

**Step 4: Re-run DAO test**

Run: `./gradlew :app:testDebugUnitTest --tests "*Dao*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/data/SubsItem.kt app/src/main/kotlin/li/songe/gkd/sdp/data/AppGroup.kt app/src/main/kotlin/li/songe/gkd/sdp/data/BlockTimeRule.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlRuleGroup.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlBlockRule.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlTimeRule.kt
git commit -m "feat: add bulk re-enable dao operations"
```

### Task 4: Background Enforcer Loop

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/App.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt` (if transaction helper needed)

**Step 1: Write failing test for interval-to-delay and force-enable execution path**

```kotlin
@Test fun `interval zero uses aggressive poll`() {
    assertEquals(15_000L, AutoReenablePolicy.nextEnforceDelayMs(0))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AutoReenable*"`
Expected: FAIL.

**Step 3: Implement enforcer service object**

```kotlin
object AutoReenableEnforcer {
    fun start() {
        appScope.launch(Dispatchers.IO) {
            while (isActive) {
                enforceAll()
                val delayMs = AutoReenablePolicy.nextEnforceDelayMs(storeFlow.value.autoReenableIntervalMinutes)
                delay(delayMs)
            }
        }
    }

    suspend fun enforceAll() {
        DbSet.subsItemDao.enableAllDisabled()
        DbSet.appGroupDao.enableAllDisabled()
        DbSet.blockTimeRuleDao.enableAllDisabled()
        DbSet.urlRuleGroupDao.enableAllDisabled()
        DbSet.urlBlockRuleDao.enableAllDisabled()
        DbSet.urlTimeRuleDao.enableAllDisabled()
    }
}
```

**Step 4: Start enforcer during app init**

In `App.onCreate()`, after `initStore()` and DB readiness, call `AutoReenableEnforcer.start()`.

**Step 5: Re-run target tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*AutoReenable*"`
Expected: PASS.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt app/src/main/kotlin/li/songe/gkd/sdp/App.kt
git commit -m "feat: add periodic auto re-enable enforcer"
```

### Task 5: UX Guardrails + Verification

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt` (text hints if needed)
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockPage.kt` (text hints if needed)

**Step 1: Add explicit copy to communicate non-disableable behavior**

- “自动重开始终启用，无法关闭”。
- “间隔可调：0~240 分钟；每 3 天仅可修改一次”。

**Step 2: Add picker constraints**

- Minute-level input allowed.
- Reject outside range with inline error/toast.

**Step 3: Run full unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

**Step 4: Build debug APK**

Run:
```powershell
$env:JAVA_HOME = 'D:\Download\tools\jdk_ms_21'
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

**Step 5: Manual acceptance checks**

- Set interval `0` -> turn off one subscription + one app blocker rule + one url rule -> verify auto restored quickly.
- Set interval `240` -> verify restore at next 4h cycle (can shorten in debug by temporarily overriding delay).
- Change interval once -> second change within 72h rejected with remaining time hint.
- No page exposes a disable switch for this feature.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockPage.kt
git commit -m "feat: enforce non-disableable auto re-enable with cooldown-limited interval edits"
```

---

## Notes and Non-Goals

- Non-goal: adding a feature toggle to disable auto re-enable.
- Hard constraints are enforced in code: `MAX_INTERVAL_MINUTES = 240`, `CHANGE_COOLDOWN_MS = 72h`.
- Interval `0` means aggressive immediate巡检, not disabled.
- This plan intentionally targets enable-state fields only (not deleting/restoring rules).
