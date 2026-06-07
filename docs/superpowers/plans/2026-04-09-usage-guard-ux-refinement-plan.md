# Usage Guard UX Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the `使用申请` settings page, request overlay, records browsing, and auto re-enable linkage so the feature feels denser, clearer, and more intentional while preserving existing runtime protection behavior.

**Architecture:** Keep `UsageGuardEngine` as the runtime authority and implement this work in three layers: pure policies for duration normalization / date grouping / app-section grouping, `UsageGuardVm` plus `SettingsStore` for persistent UX state, and Compose/UI refactors in `UsageGuardPage` and `UsageGuardRequestOverlayService`. Extend `AutoReenableEnforcer` only to restore the `usageGuardEnabled` switch, without touching other `UsageGuard` preferences.

**Tech Stack:** Kotlin, Jetpack Compose Material3, file-backed `SettingsStore`, Room entities/DAOs, Kotlin Flow, `LifecycleService` overlays, JUnit4 unit tests.

---

**Design Context:** Before changing any UI, read `.impeccable.md` and follow it. The key principles are: stronger grouping, less vertical bloat, primary actions should feel constrained and ritualized, Android-native behavior with more deliberate hierarchy, and explicit trust-building feedback after saves and mode changes.

## File Map

**Create:**
- `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicy.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicy.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicyTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicyTest.kt`

**Modify:**
- `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardVmTest.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Responsibilities:**
- `UsageGuardUiStatePolicy.kt`: normalize fixed duration options and group selected apps into strict/resumable sections.
- `UsageGuardHistoryPolicy.kt`: bucket and filter `UsageGuardRecord` items by local date.
- `UsageGuardRecord.kt`: add DAO support for querying records by selected local-day range.
- `SettingsStore.kt`: persist four fixed `UsageGuard` duration options.
- `UsageGuardVm.kt`: expose grouped app sections, selected-day records, duration settings, and save actions with success feedback.
- `UsageGuardPage.kt`: rebuild the page into stronger sections, icon-led app management, and date-filtered records.
- `UsageGuardRequestOverlayService.kt`: refine the request form with collapsed add-tag UI, live reason count, and settings-driven duration options.
- `AutoReenableEnforcer.kt`: include `usageGuardEnabled` in the periodic restore loop.
- `FocusLockPage.kt`: update auto re-enable copy so the user can see that `使用申请` is also protected.

---

### Task 1: Lock pure UI-state and history behavior with tests first

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicyTest.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicy.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicy.kt`

- [ ] **Step 1: Write the failing tests for fixed duration-option normalization and selected-app grouping**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardAppProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardUiStatePolicyTest {
    @Test
    fun normalizeDurationOptionsPadsAndCleansInvalidValues() {
        val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(
            raw = listOf(0, 25, -3),
        )

        assertEquals(listOf(25, 10, 15, 30), normalized)
    }

    @Test
    fun groupSelectedAppsSplitsStrictAndResumableUsingDefaultGrantMode() {
        val grouped = UsageGuardUiStatePolicy.groupSelectedApps(
            profiles = listOf(
                UsageGuardAppProfile(appId = "strict.app", selectedTarget = true, grantMode = UsageGuardPolicy.GRANT_MODE_STRICT),
                UsageGuardAppProfile(appId = "default.app", selectedTarget = true, grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE),
                UsageGuardAppProfile(appId = "ignored.app", selectedTarget = false, grantMode = UsageGuardPolicy.GRANT_MODE_STRICT),
            ),
            defaultGrantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        )

        assertEquals(listOf("strict.app"), grouped.strictAppIds)
        assertEquals(listOf("default.app"), grouped.resumableAppIds)
    }
}
```

- [ ] **Step 2: Write the failing tests for date bucketing and date filtering**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageGuardHistoryPolicyTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun bucketRecordsByDateGroupsTodayBeforeOlderDays() {
        val records = listOf(
            recordAt(2026, 4, 9, 21, 0, "night.app"),
            recordAt(2026, 4, 9, 8, 0, "morning.app"),
            recordAt(2026, 4, 8, 18, 0, "yesterday.app"),
        )

        val grouped = UsageGuardHistoryPolicy.bucketRecordsByDate(records, zoneId)

        assertEquals(LocalDate.of(2026, 4, 9), grouped[0].date)
        assertEquals(2, grouped[0].records.size)
        assertEquals(LocalDate.of(2026, 4, 8), grouped[1].date)
    }

    @Test
    fun recordsForDateReturnsOnlySelectedDayRecords() {
        val records = listOf(
            recordAt(2026, 4, 9, 21, 0, "today.app"),
            recordAt(2026, 4, 8, 18, 0, "yesterday.app"),
        )

        val filtered = UsageGuardHistoryPolicy.recordsForDate(
            records = records,
            date = LocalDate.of(2026, 4, 8),
            zoneId = zoneId,
        )

        assertEquals(listOf("yesterday.app"), filtered.map { it.appId })
    }

    @Test
    fun dayRangeCoversWholeSelectedLocalDate() {
        val (startAt, endAt) = UsageGuardHistoryPolicy.dayRange(
            date = LocalDate.of(2026, 4, 8),
            zoneId = zoneId,
        )

        val start = java.time.Instant.ofEpochMilli(startAt).atZone(zoneId).toLocalDateTime()
        val end = java.time.Instant.ofEpochMilli(endAt - 1).atZone(zoneId).toLocalDateTime()

        assertEquals(LocalDateTime.of(2026, 4, 8, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 4, 8, 23, 59, 59, 999_000_000), end)
    }

    private fun recordAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, appId: String): UsageGuardRecord {
        val requestedAt = LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        return UsageGuardRecord(
            id = requestedAt,
            appId = appId,
            appName = appId,
            tagNames = listOf("查资料"),
            reasonText = "临时申请",
            requestedDurationMinutes = 15,
            requestedAt = requestedAt,
            grantedAt = requestedAt,
            expiresAt = requestedAt + 15 * 60_000L,
        )
    }
}
```

- [ ] **Step 3: Run the new tests to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardUiStatePolicyTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardHistoryPolicyTest"
```

Expected:
- both commands FAIL because the policy files do not exist yet

- [ ] **Step 4: Implement the pure UI-state policy**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardAppProfile

object UsageGuardUiStatePolicy {
    val defaultDurationOptions = listOf(10, 15, 30, 60)

    data class SelectedAppSections(
        val strictAppIds: List<String>,
        val resumableAppIds: List<String>,
    )

    fun normalizeDurationOptions(raw: List<Int>): List<Int> {
        val cleaned = raw.filter { it > 0 }.take(4).toMutableList()
        defaultDurationOptions.forEach { fallback ->
            if (cleaned.size < 4) cleaned += fallback
        }
        return cleaned.take(4)
    }

    fun groupSelectedApps(
        profiles: List<UsageGuardAppProfile>,
        defaultGrantMode: Int,
    ): SelectedAppSections {
        val selected = profiles.filter { it.selectedTarget }
        val strict = selected.filter {
            resolvedGrantMode(it.grantMode, defaultGrantMode) == UsageGuardPolicy.GRANT_MODE_STRICT
        }.map { it.appId }
        val resumable = selected.filter {
            resolvedGrantMode(it.grantMode, defaultGrantMode) == UsageGuardPolicy.GRANT_MODE_RESUMABLE
        }.map { it.appId }
        return SelectedAppSections(strictAppIds = strict, resumableAppIds = resumable)
    }

    private fun resolvedGrantMode(grantMode: Int, defaultGrantMode: Int): Int {
        return when (grantMode) {
            UsageGuardPolicy.GRANT_MODE_STRICT,
            UsageGuardPolicy.GRANT_MODE_RESUMABLE -> grantMode
            else -> defaultGrantMode
        }
    }
}
```

- [ ] **Step 5: Implement the pure history policy**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageGuardHistoryPolicy {
    data class DayBucket(
        val date: LocalDate,
        val records: List<UsageGuardRecord>,
    )

    fun bucketRecordsByDate(
        records: List<UsageGuardRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DayBucket> {
        return records
            .groupBy { Instant.ofEpochMilli(it.requestedAt).atZone(zoneId).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
            .map { (date, dayRecords) ->
                DayBucket(
                    date = date,
                    records = dayRecords.sortedByDescending { it.requestedAt },
                )
            }
    }

    fun dayRange(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        val startAt = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startAt to endAt
    }

    fun recordsForDate(
        records: List<UsageGuardRecord>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<UsageGuardRecord> {
        return records
            .filter { Instant.ofEpochMilli(it.requestedAt).atZone(zoneId).toLocalDate() == date }
            .sortedByDescending { it.requestedAt }
    }
}
```

- [ ] **Step 6: Re-run the tests to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardUiStatePolicyTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardHistoryPolicyTest"
```

Expected:
- both commands PASS

- [ ] **Step 7: Commit the pure policies**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicy.kt app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardUiStatePolicyTest.kt app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardHistoryPolicyTest.kt
git commit -m "test: lock usage guard ux policies"
```

---

### Task 2: Persist four fixed duration options and extend UsageGuardVm state/actions

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardVmTest.kt`
- Reference: `.impeccable.md`

- [ ] **Step 1: Add a day-range DAO query and the new fixed duration-options field**

In `UsageGuardRecord.kt`, extend the DAO with:

```kotlin
@Query(
    """
    SELECT * FROM usage_guard_record
    WHERE requested_at >= :startAt AND requested_at < :endAt
    ORDER BY requested_at DESC
    """
)
fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>>
```

In `SettingsStore.kt`, add:

```kotlin
val usageGuardEnabled: Boolean = false,
val usageGuardScopeMode: Int = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
val usageGuardDefaultGrantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
val usageGuardMinReasonLength: Int = 8,
val usageGuardDurationOptionsMinutes: List<Int> = UsageGuardUiStatePolicy.defaultDurationOptions,
```

- [ ] **Step 2: Extend UsageGuardVm tests for duration normalization and record-day defaulting**

```kotlin
@Test
fun durationOptionsAlwaysNormalizeToFourPositiveValues() {
    val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(listOf(5, 0, -1, 25))
    assertEquals(listOf(5, 25, 10, 15), normalized)
}

@Test
fun oppositeGrantModeStillFlipsStrictAndResumable() {
    assertEquals(
        UsageGuardPolicy.GRANT_MODE_STRICT,
        UsageGuardVm.oppositeGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE)
    )
    assertEquals(
        UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        UsageGuardVm.oppositeGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT)
    )
}
```

- [ ] **Step 3: Run the ViewModel test target to verify RED if references are missing**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardVmTest"
```

Expected:
- FAIL if `UsageGuardVm` or `SettingsStore` references the new duration-options field incorrectly

- [ ] **Step 4: Extend UsageGuardVm with duration-option actions, grouped-app helpers, and save feedback**

```kotlin
class UsageGuardVm : BaseViewModel() {
    val appProfilesFlow = DbSet.usageGuardAppProfileDao.queryAll().stateInit(emptyList())
    val tagsFlow = DbSet.usageGuardTagDao.queryAll().stateInit(emptyList())
    private val selectedHistoryDateFlow = MutableStateFlow(LocalDate.now())
    val historyFlow = selectedHistoryDateFlow.flatMapLatest { date ->
        val (startAt, endAt) = UsageGuardHistoryPolicy.dayRange(date)
        DbSet.usageGuardRecordDao.queryByRequestedAtRange(startAt, endAt)
    }.stateInit(emptyList())

    fun updateMinReasonLength(minLength: Int) {
        storeFlow.update { it.copy(usageGuardMinReasonLength = minLength.coerceAtLeast(1)) }
        toast("最少理由字数已保存")
    }

    fun updateDurationOptions(raw: List<Int>) {
        val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(raw)
        storeFlow.update { it.copy(usageGuardDurationOptionsMinutes = normalized) }
        toast("申请时长选项已保存")
    }

    fun moveSelectedAppToGrantMode(appId: String, grantMode: Int) {
        saveAppGrantMode(appId, grantMode)
    }

    fun updateSelectedHistoryDate(date: LocalDate) {
        selectedHistoryDateFlow.value = date
    }

    companion object {
        val presetTags = listOf("联系工作", "回复消息", "查资料", "支付", "其他")

        fun shouldRetainProfile(profile: UsageGuardAppProfile, defaultGrantMode: Int): Boolean {
            return profile.selectedTarget || profile.globalWhitelist || profile.grantMode != defaultGrantMode
        }

        fun oppositeGrantMode(defaultGrantMode: Int): Int {
            return if (defaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                UsageGuardPolicy.GRANT_MODE_RESUMABLE
            } else {
                UsageGuardPolicy.GRANT_MODE_STRICT
            }
        }
    }
}
```

- [ ] **Step 5: Re-run ViewModel tests to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardVmTest"
```

Expected:
- PASS

- [ ] **Step 6: Commit the persistence and ViewModel changes**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardVmTest.kt
git commit -m "feat: persist usage guard duration preferences"
```

---

### Task 3: Rebuild UsageGuardPage into layered sections and icon-led app management

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt`
- Reference: `.impeccable.md`

- [ ] **Step 1: Replace the single long-form page flow with four sections**

Implement these section headers in order:

```kotlin
1. 保护状态
2. 规则与申请偏好
3. 应用管理
4. 记录浏览
```

And add an implementation note near the top of the file:

```kotlin
// UI in this page must follow .impeccable.md:
// stronger grouping, less vertical bloat, deliberate feedback, and a more ritualized feel.
```

- [ ] **Step 2: Rebuild the selected-app area into strict/resumable icon sections**

Add an icon-led board component:

```kotlin
@Composable
private fun SelectedAppModeBoard(
    title: String,
    appIds: List<String>,
    appInfoMap: Map<String, AppInfo>,
    onAppClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            appIds.forEach { appId ->
                Column(
                    modifier = Modifier.width(72.dp).clickable { onAppClick(appId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(appId = appId, modifier = Modifier.size(48.dp))
                    Text(
                        text = appInfoMap[appId]?.name ?: appId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
```

Use `UsageGuardUiStatePolicy.groupSelectedApps(...)` to populate:

```kotlin
val grouped = UsageGuardUiStatePolicy.groupSelectedApps(
    profiles = appProfiles,
    defaultGrantMode = settings.usageGuardDefaultGrantMode,
)
```

- [ ] **Step 3: Add tap-to-edit mode switching and keep a drag target structure**

For tap flow, open a modal or bottom sheet with:

```kotlin
FilterChip(
    selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
    onClick = { vm.moveSelectedAppToGrantMode(appId, UsageGuardPolicy.GRANT_MODE_STRICT) },
    label = { Text("严格模式") },
)
FilterChip(
    selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
    onClick = { vm.moveSelectedAppToGrantMode(appId, UsageGuardPolicy.GRANT_MODE_RESUMABLE) },
    label = { Text("普通模式") },
)
TextButton(onClick = { vm.saveSelectedTargets(selectedTargetApps - appId) }) {
    Text("移出受控应用")
}
```

For drag support, wire each app chip with a drag state and drop target keyed by grant mode. If the drag gesture proves unstable, keep the tap flow fully functional and ship the drag path behind the same board structure.

- [ ] **Step 4: Rebuild whitelist and records browsing**

Whitelist should move to icon-led presentation:

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    whitelistApps.forEach { appId ->
        Column(
            modifier = Modifier.width(72.dp).clickable { /* open whitelist action sheet */ },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(appId = appId, modifier = Modifier.size(48.dp))
            Text(
                text = appInfoMap[appId]?.name ?: appId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
```

Records should switch to date filtering:

```kotlin
var selectedDate by remember { mutableStateOf(LocalDate.now()) }
```

And render:

```kotlin
Text("记录浏览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
TextButton(onClick = { showDatePicker = true }) { Text(selectedDate.toString()) }
LaunchedEffect(selectedDate) { vm.updateSelectedHistoryDate(selectedDate) }
if (history.isEmpty()) {
    Text("所选日期暂无记录")
} else {
    history.forEachIndexed { index, record ->
        HistoryRow(record = record, appName = appInfoMap[record.appId]?.name ?: record.appName)
        if (index != history.lastIndex) HorizontalDivider()
    }
}
```

- [ ] **Step 5: Run focused UsageGuard tests after the page refactor**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardUiStatePolicyTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardHistoryPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardVmTest"
```

Expected:
- all commands PASS

- [ ] **Step 6: Commit the page refactor**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt
git commit -m "feat: refine usage guard settings layout"
```

---

### Task 4: Refine the request overlay form for staged tags, live count, and fixed duration choices

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Reference: `.impeccable.md`

- [ ] **Step 1: Read fixed duration options from settings instead of hardcoding them**

Replace:

```kotlin
val durationOptions = listOf(10, 15, 30, 60)
```

With:

```kotlin
val settings by storeFlow.collectAsState()
val durationOptions = remember(settings.usageGuardDurationOptionsMinutes) {
    UsageGuardUiStatePolicy.normalizeDurationOptions(settings.usageGuardDurationOptionsMinutes)
}
```

- [ ] **Step 2: Collapse add-tag UI by default**

Add local state:

```kotlin
var showAddTagEditor by remember { mutableStateOf(false) }
```

Render:

```kotlin
TextButton(onClick = { showAddTagEditor = !showAddTagEditor }) {
    Text(if (showAddTagEditor) "收起添加标签" else "没有合适的标签？添加标签")
}
if (showAddTagEditor) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            modifier = Modifier.weight(1f),
            label = { Text("添加标签") },
            singleLine = true,
        )
        Button(
            onClick = {
                val normalized = newTagText.trim()
                if (normalized.isBlank()) return@Button
                onAddTag(normalized, tags)
                selectedTags = selectedTags + normalized
                newTagText = ""
                showAddTagEditor = false
            },
        ) {
            Text("加入")
        }
    }
}
```

- [ ] **Step 3: Add a live character count to the reason field**

Render the field as:

```kotlin
OutlinedTextField(
    value = reasonText,
    onValueChange = {
        reasonError = null
        reasonText = it
    },
    modifier = Modifier.fillMaxWidth(),
    label = { Text("申请理由") },
    supportingText = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("至少 $minReasonLength 个字")
            Text("${reasonText.trim().length} 字")
        }
    },
    isError = reasonError != null,
    minLines = 3,
)
```

- [ ] **Step 4: Make the configured four options the dominant duration path**

Render the options directly from settings:

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    durationOptions.forEach { minutes ->
        FilterChip(
            selected = selectedDuration == minutes,
            onClick = {
                durationError = null
                selectedDuration = minutes
            },
            label = { Text("${minutes}分钟") },
        )
    }
}
```

And remove the always-visible custom minutes input from the primary flow. If you keep custom input at all, place it under an explicit secondary action such as:

```kotlin
TextButton(onClick = { showCustomDuration = !showCustomDuration }) {
    Text(if (showCustomDuration) "收起自定义时长" else "自定义时长")
}
```

- [ ] **Step 5: Run focused UsageGuard tests after overlay changes**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuard*Test"
```

Expected:
- PASS

- [ ] **Step 6: Commit the overlay refinement**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt
git commit -m "feat: refine usage guard request overlay"
```

---

### Task 5: Include UsageGuard in auto re-enable protection

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

- [ ] **Step 1: Extend the existing enforcer test to require UsageGuard switch recovery**

Add this test to `AutoReenableEnforcerTest.kt`:

```kotlin
@Test
fun defaultOperationsIncludeUsageGuardSwitchRecovery() {
    assertTrue(AutoReenableEnforcer.defaultOperationNames().contains("usage_guard_switch"))
}
```

- [ ] **Step 2: Run the service test target to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*AutoReenableEnforcerTest"
```

Expected:
- FAIL because the new operation name is not yet present

- [ ] **Step 3: Add the new enable operation without touching other UsageGuard settings**

Extend `defaultEnableOperationEntries()`:

```kotlin
private fun defaultEnableOperationEntries(): List<Pair<String, suspend () -> Int>> {
    return listOf(
        "subs_item" to { DbSet.subsItemDao.enableAllDisabled() },
        "app_config" to { DbSet.appConfigDao.enableAllDisabled() },
        "app_group" to { DbSet.appGroupDao.enableAllDisabled() },
        "block_time_rule" to { DbSet.blockTimeRuleDao.enableAllDisabled() },
        "url_rule_group" to { DbSet.urlRuleGroupDao.enableAllDisabled() },
        "url_block_rule" to { DbSet.urlBlockRuleDao.enableAllDisabled() },
        "url_time_rule" to { DbSet.urlTimeRuleDao.enableAllDisabled() },
        "usage_guard_switch" to {
            val settings = storeFlow.value
            if (settings.usageGuardEnabled) {
                0
            } else {
                storeFlow.update { it.copy(usageGuardEnabled = true) }
                1
            }
        },
    )
}
```

- [ ] **Step 4: Update the visible auto re-enable copy so users know UsageGuard is covered**

In `FocusLockPage.kt`, replace the main explanatory copy:

```kotlin
Text("自动重开始终启用，无法关闭。会恢复已关闭的规则与使用申请开关。")
```

And in the card:

```kotlin
Text(
    text = "自动重开始终启用，无法关闭；会恢复规则与使用申请开关",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.primary
)
```

- [ ] **Step 5: Re-run the auto re-enable tests to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*AutoReenableEnforcerTest"
.\gradlew :app:testGkdDebugUnitTest --tests "*AutoReenableDisableGuardTest"
```

Expected:
- both commands PASS

- [ ] **Step 6: Commit the auto re-enable linkage**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt
git commit -m "feat: auto reenable usage guard switch"
```

---

### Task 6: Full verification and acceptance

**Files:**
- No new files
- Reference: `.impeccable.md`

- [ ] **Step 1: Run the exact automated checks**

```powershell
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardUiStatePolicyTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardHistoryPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardVmTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*AutoReenableEnforcerTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuard*Test"
```

Expected:
- all commands end with `BUILD SUCCESSFUL`

- [ ] **Step 2: Manual acceptance for page structure and app management**

Check:
1. Open `数字自律 -> 使用申请`.
2. Confirm the page is visually split into `保护状态 / 规则与申请偏好 / 应用管理 / 记录浏览`.
3. In `仅选中应用`, confirm apps appear under `严格模式` and `普通模式`.
4. Tap an app icon and confirm mode switching works.
5. Drag an app icon into the other section and confirm the mode changes.
6. Switch to global scope and confirm the whitelist appears as an icon-led strip/flow instead of a long vertical list.

- [ ] **Step 3: Manual acceptance for request overlay**

Check:
1. Trigger the request overlay by opening a protected app.
2. Confirm `添加标签` is collapsed by default.
3. Expand it only when needed and add a custom tag.
4. Confirm the reason field shows a live character count.
5. Confirm the four quick duration options match the values configured in settings.
6. Save a different minimum reason length in settings and confirm the page shows a success toast.

- [ ] **Step 4: Manual acceptance for records and auto re-enable**

Check:
1. Open `记录浏览` and confirm it defaults to today’s records.
2. Switch the date filter and confirm only the selected date’s records are shown.
3. Turn off `使用申请总开关`.
4. Wait for the next auto re-enable enforcement interval or trigger the enforcer in a test build.
5. Confirm only the main `使用申请总开关` is restored, without changing scope, default grant mode, minimum reason length, or the four fixed duration options.

- [ ] **Step 5: Report exact verification results**

Do not mark the work complete until you have:
- quoted the exact Gradle results
- stated whether drag interaction was verified on device
- stated whether date filtering was verified with records from more than one day
- stated whether auto re-enable was manually observed restoring the `使用申请总开关`
