# Auto Re-enable Daily Disable Quota Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a global daily disable quota (1~5), shared across all disable actions; after quota is exhausted, disabling is blocked until next local day (00:00).

**Architecture:** Keep policy state in `SettingsStore` (no new Room tables), and enforce via a single guard utility called by every disable entry point before writing `enabled=false`. Batch disable consumes exactly 1 quota unit. Day reset is based on local timezone day-start epoch.

**Tech Stack:** Kotlin, Jetpack Compose, MVVM, MutableStateFlow (`storeFlow`), existing unit test stack (`./gradlew :app:testGkdDebugUnitTest`).

---

## Handoff Status (2026-02-21, for next AI)

### Workspace / branch
- Worktree: `D:\Project\gkd-SDP\.worktrees\auto-reenable-daily-disable-quota`
- Branch: `feat/auto-reenable-daily-disable-quota`

### Completed (already implemented in worktree)
- Task 1: `SettingsStore` daily quota fields added.
- Task 2: `AutoReenablePolicy` daily limit/day-start helpers added, and tests extended.
- Task 3: `AutoReenableDisableGuard` created with evaluator + store mutator, and tests added.
- Partial Task 6: `RuleGroupState` batch disable path already has quota pre-consume guard.

### Incomplete / needs continuation
- Task 4: `FocusLockVm` daily quota UI state + setter + guard integration not finished.
- Task 5: `FocusLockPage` quota input / usage display / save flow not finished.
- Task 6: Disable-entry guard is still missing in:
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SubsManagePage.kt`
- Task 7: Added `*DisableQuotaTest` files are placeholder-level and will not pass until Task 4/6 code is completed.
- Task 8: Full verification/build + Agent handoff update not done.

### Important caution before continuing
- Some files are currently dirty only due line-ending rewrite (no intended logic change):
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockVm.kt`
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SubsManagePage.kt`
- Next AI should normalize this first (either discard pure EOL-only changes or re-apply intended edits carefully via UTF-8-safe patching), then continue Task 4+.

### Suggested next step order
1. Clean/normalize EOL-only dirty files listed above.
2. Finish Task 4 and Task 5 first (VM + UI contract).
3. Finish Task 6 all entry-point guards.
4. Align Task 7 tests with final VM/API shape.
5. Run:
   - `$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest --tests "*AutoReenablePolicyTest" --tests "*AutoReenableDisableGuardTest" --tests "*FocusLockVmAutoReenableTest" --tests "*DisableQuotaTest"`
   - `$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest`
   - `$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:assembleGkdDebug`

---

### Task 1: Extend settings model for quota state

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`

**Step 1: Add new fields to `SettingsStore`**

```kotlin
val autoReenableDailyDisableLimit: Int = 1,
val autoReenableDailyDisableUsed: Int = 0,
val autoReenableDailyDisableDayStartAt: Long = 0L,
```

**Step 2: Keep defaults safe**

- Limit default is `1` (strict by default, matches product intent).
- Used/dayStart default to `0` so first access can lazily initialize.

**Step 3: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt
git commit -m "feat(auto-reenable): add daily disable quota settings fields"
```

### Task 2: Add policy helper for daily reset and quota normalization

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicy.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicyTest.kt`

**Step 1: Add limit normalization and day-start helpers**

```kotlin
const val MIN_DAILY_DISABLE_LIMIT = 1
const val MAX_DAILY_DISABLE_LIMIT = 5

fun normalizeDailyDisableLimit(value: Int): Int = value.coerceIn(MIN_DAILY_DISABLE_LIMIT, MAX_DAILY_DISABLE_LIMIT)

fun localDayStartEpochMs(now: Long): Long { /* ZoneId.systemDefault + LocalDate */ }

fun shouldResetDailyCounter(dayStartAt: Long, now: Long): Boolean = dayStartAt != localDayStartEpochMs(now)
```

**Step 2: Add unit tests**

- Test limit clamp: `0 -> 1`, `9 -> 5`.
- Test day reset: same day does not reset; next day resets.

**Step 3: Run targeted tests**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest --tests "*AutoReenablePolicyTest"
```

Expected: PASS.

**Step 4: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenablePolicyTest.kt
git commit -m "feat(auto-reenable): add daily disable quota policy helpers"
```

### Task 3: Implement global disable guard (single source of truth)

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenableDisableGuard.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenableDisableGuardTest.kt`

**Step 1: Create guard result model**

```kotlin
data class DisableAttemptResult(
    val allowed: Boolean,
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val dayStartAt: Long
)
```

**Step 2: Implement pure evaluator + store mutator**

- Pure function to evaluate/consume quota:
  - normalize limit,
  - reset used when day changes,
  - if `consume=false`, only query state,
  - if `consume=true`, consume exactly 1 unit.
- Side-effect function using `storeFlow.update`:
  - invoked only for disable actions.

**Step 3: Add tests for edge cases**

- Fresh day first disable allowed.
- Used == limit blocks.
- Next local day auto-resets.
- Batch semantic: one consume call increments by 1 only.

**Step 4: Run targeted tests**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest --tests "*AutoReenableDisableGuardTest"
```

Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/AutoReenableDisableGuard.kt app/src/test/kotlin/li/songe/gkd/sdp/util/AutoReenableDisableGuardTest.kt
git commit -m "feat(auto-reenable): add global daily disable guard"
```

### Task 4: Extend FocusLock VM state and setting update API

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/ui/FocusLockVmAutoReenableTest.kt`

**Step 1: Extend auto-reenable UI model**

```kotlin
data class AutoReenableUiState(
    val canEditInterval: Boolean,
    val nextEditableAt: Long,
    val nextEnforceAt: Long,
    val dailyDisableLimit: Int,
    val dailyDisableUsed: Int,
    val dailyDisableRemaining: Int,
    val nextDailyResetAt: Long
)
```

**Step 2: Add setter for daily limit**

- Method: `updateAutoReenableDailyDisableLimit(requestedLimit: Int)`.
- Normalize to `1..5`.
- Keep interval cooldown logic unchanged (daily limit setting should not be blocked by 72h interval cooldown).

**Step 3: Add pure tests**

- UI state remaining = limit-used.
- Limit update normalization works.

**Step 4: Run targeted tests**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest --tests "*FocusLockVmAutoReenableTest"
```

Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/FocusLockVmAutoReenableTest.kt
git commit -m "feat(focus-lock): expose daily disable quota in vm state"
```

### Task 5: Add quota config and usage display in FocusLock page

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Step 1: Extend Auto Re-enable dialog**

- Add numeric input for daily disable limit (1~5).
- Show `今日已用/总额` and `剩余次数`.
- Show `下次重置时间` (`明日 00:00` local formatted time).

**Step 2: Hook save action**

- Existing interval save remains.
- Add call to `vm.updateAutoReenableDailyDisableLimit(parsedLimit)`.

**Step 3: Add validation messaging**

- Invalid value message: "请输入 1~5 的整数次数".

**Step 4: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt
git commit -m "feat(focus-lock): add daily disable quota settings ui"
```

### Task 6: Enforce guard in all disable entry points

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SubsManagePage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/RuleGroupState.kt`

**Step 1: Define common rule**

- Only consume quota when an action transitions from enabled -> disabled.
- Batch disable operation consumes exactly one quota before batch loop.
- Enabling should never consume quota.

**Step 2: Add pre-check before writes**

```kotlin
val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
if (!attempt.allowed) {
    toast("今日关闭次数已用尽（${attempt.limit}次），将于明日 00:00 重置")
    return@launch
}
```

**Step 3: Apply to all toggles**

- `toggleGroupEnabled`, `toggleRuleEnabled`, `toggleUrlRuleEnabled`, `toggleTimeRuleEnabled`, `updateEnable(...)`, and batch group disable path in `RuleGroupState`.

**Step 4: Commit**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SubsManagePage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/component/RuleGroupState.kt
git commit -m "feat(auto-reenable): enforce global daily disable quota across disable entries"
```

### Task 7: Add integration-style VM tests for quota enforcement

**Files:**
- Create or modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/FocusLockVmDisableQuotaTest.kt`
- Create or modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/UrlBlockVmDisableQuotaTest.kt`
- Create or modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmDisableQuotaTest.kt`

**Step 1: Add tests for allowed -> blocked transition**

- Setup limit=1, used=0: first disable allowed.
- Second disable same day blocked.

**Step 2: Add day rollover test**

- Same setup with day change: disable allowed again after reset day-start.

**Step 3: Run targeted test classes**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest --tests "*DisableQuotaTest"
```

Expected: PASS.

**Step 4: Commit**

```bash
git add app/src/test/kotlin/li/songe/gkd/sdp/ui/FocusLockVmDisableQuotaTest.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/UrlBlockVmDisableQuotaTest.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmDisableQuotaTest.kt
git commit -m "test(auto-reenable): cover daily disable quota enforcement"
```

### Task 8: Full verification and handoff update

**Files:**
- Modify: `Agent.md` (only section 4 snapshot + maintenance record line, if status changed)

**Step 1: Run full test command**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:testGkdDebugUnitTest
```

Expected: PASS.

**Step 2: Build debug APK**

```bash
$env:JAVA_HOME='D:\Download\tools\jdk_ms_21'; ./gradlew :app:assembleGkdDebug
```

Expected: BUILD SUCCESSFUL.

**Step 3: Manual acceptance checklist**

- Limit set to 1, first disable succeeds.
- Same day second disable blocked with explicit toast.
- Batch disable consumes 1 count only.
- At local next day 00:00+, disable available again.

**Step 4: Commit docs handoff update (if needed)**

```bash
git add Agent.md
git commit -m "docs(agent): refresh snapshot for daily disable quota"
```
