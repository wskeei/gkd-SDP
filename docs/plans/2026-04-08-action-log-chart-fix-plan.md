# Action Log Chart Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the trigger-record statistics chart reflect the most recent 14 local calendar days correctly, including zero-count days, so the chart and the detail list are stable and truthful.

**Architecture:** Keep the Room aggregation query simple and continue querying per-day counts from `action_log`. Move the date-window calculation and sparse-series normalization into a pure Kotlin helper so the behavior is deterministic under unit tests. `ActionLogVm` will request raw daily counts for the correct local-day-aligned time range, normalize them into a fixed 14-day series, and `ActionLogPage` will render both the chart and the detail list from that same normalized dataset.

**Tech Stack:** Kotlin, Room, Kotlin Flow, Jetpack Compose Material3, Vico chart library, JUnit4 unit tests.

---

## File Map

**Create:**
- `app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt`

**Modify:**
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogVm.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt`

**Leave unchanged unless investigation during implementation proves otherwise:**
- `app/src/main/kotlin/li/songe/gkd/sdp/data/ActionLog.kt`

**Responsibilities:**
- `ActionLogStatsPolicy.kt`: local-day window calculation and sparse-to-continuous daily-series normalization.
- `ActionLogStatsPolicyTest.kt`: regression coverage for local-day alignment, missing-day zero fill, and stable ordering.
- `ActionLogVm.kt`: wire DAO output through the policy helper and expose normalized chart data.
- `ActionLogPage.kt`: consume normalized stats consistently in chart labels, chart bars, and detail rows.

---

### Task 1: Add failing tests for the statistics policy

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt`

- [ ] **Step 1: Write the failing test for local calendar-day alignment**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActionLogStatsPolicyTest {
    @Test
    fun recentWindowStartUsesLocalCalendarDayBoundary() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 15, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val start = ActionLogStatsPolicy.windowStartEpochMs(
            now = now,
            days = 14,
            zoneId = zoneId
        )

        val expected = LocalDateTime.of(2026, 3, 26, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, start)
    }
}
```

- [ ] **Step 2: Write the failing test for zero-filled missing days**

```kotlin
@Test
fun normalizeDailyStatsFillsMissingDaysWithZero() {
    val zoneId = ZoneId.systemDefault()
    val now = LocalDateTime.of(2026, 4, 8, 15, 30)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()

    val normalized = ActionLogStatsPolicy.normalizeDailyStats(
        rawStats = listOf(
            DailyStat(date = "2026-04-06", count = 3),
            DailyStat(date = "2026-04-08", count = 1),
        ),
        now = now,
        days = 3,
        zoneId = zoneId
    )

    assertEquals(
        listOf(
            DailyStat(date = "2026-04-06", count = 3),
            DailyStat(date = "2026-04-07", count = 0),
            DailyStat(date = "2026-04-08", count = 1),
        ),
        normalized
    )
}
```

- [ ] **Step 3: Write the failing test for ignoring out-of-window rows**

```kotlin
@Test
fun normalizeDailyStatsDropsRowsOutsideRequestedWindow() {
    val zoneId = ZoneId.systemDefault()
    val now = LocalDateTime.of(2026, 4, 8, 15, 30)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()

    val normalized = ActionLogStatsPolicy.normalizeDailyStats(
        rawStats = listOf(
            DailyStat(date = "2026-04-01", count = 9),
            DailyStat(date = "2026-04-08", count = 1),
        ),
        now = now,
        days = 3,
        zoneId = zoneId
    )

    assertEquals(3, normalized.size)
    assertEquals("2026-04-06", normalized.first().date)
    assertEquals(0, normalized[0].count)
    assertEquals(1, normalized.last().count)
}
```

- [ ] **Step 4: Run the focused test class to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*ActionLogStatsPolicyTest"
```

Expected: compilation failure because `ActionLogStatsPolicy` does not exist yet, or test failure because helper behavior is not implemented.

- [ ] **Step 5: Commit the failing test scaffold**

```powershell
git add app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt
git commit -m "test: add failing coverage for action log chart stats"
```

---

### Task 2: Implement the pure statistics policy helper

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt`

- [ ] **Step 1: Add the helper with local-day-aligned window start and normalization**

```kotlin
package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.DailyStat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ActionLogStatsPolicy {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun windowStartEpochMs(
        now: Long,
        days: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(days > 0)
        val endDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val startDate = endDate.minusDays((days - 1).toLong())
        return startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun normalizeDailyStats(
        rawStats: List<DailyStat>,
        now: Long,
        days: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DailyStat> {
        require(days > 0)
        val endDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val startDate = endDate.minusDays((days - 1).toLong())
        val countByDate = rawStats.associate { it.date to it.count }

        return (0 until days).map { index ->
            val date = startDate.plusDays(index.toLong()).format(dateFormatter)
            DailyStat(
                date = date,
                count = countByDate[date] ?: 0
            )
        }
    }
}
```

- [ ] **Step 2: Run the focused test class to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*ActionLogStatsPolicyTest"
```

Expected: PASS.

- [ ] **Step 3: Refactor only if needed**

Allowed refactor:
```kotlin
private fun localDateRange(
    now: Long,
    days: Int,
    zoneId: ZoneId,
): Pair<java.time.LocalDate, java.time.LocalDate>
```

Do not add extra features such as weekly grouping or timezone overrides from settings.

- [ ] **Step 4: Commit the helper**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt
git commit -m "feat: normalize action log daily chart stats"
```

---

### Task 3: Wire the ViewModel to use the normalized 14-day series

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt` only if Task 2 revealed a missing helper

- [ ] **Step 1: Replace the rolling 14x24h cutoff with a local-day-aligned cutoff**

Target shape:
```kotlin
private const val CHART_DAYS = 14

private val statsNow = System.currentTimeMillis()
private val statsWindowStart = ActionLogStatsPolicy.windowStartEpochMs(
    now = statsNow,
    days = CHART_DAYS
)
```

- [ ] **Step 2: Map raw DAO rows into a fixed 14-day normalized series**

Target shape:
```kotlin
val dailyStatsFlow: StateFlow<List<DailyStat>> = DbSet.actionLogDao.queryDailyStats(
    startTime = statsWindowStart,
    subsId = args.subsId,
    appId = args.appId
).map { rawStats ->
    ActionLogStatsPolicy.normalizeDailyStats(
        rawStats = rawStats,
        now = statsNow,
        days = CHART_DAYS
    )
}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

- [ ] **Step 3: Keep the paging/list code unchanged**

Do not mix chart normalization into `pagingDataFlow`. The record list and the chart are separate read models.

- [ ] **Step 4: Run focused tests to verify nothing regressed in the helper path**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*ActionLogStatsPolicyTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the VM wiring**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogVm.kt app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt
git commit -m "fix: align action log stats to local calendar days"
```

---

### Task 4: Make the UI consume the normalized series consistently

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt`

- [ ] **Step 1: Update the statistics title to match the real semantics**

Replace:
```kotlin
text = "触发趋势 (最近14天)"
```

With:
```kotlin
text = "触发趋势（最近14个自然日）"
```

- [ ] **Step 2: Keep chart bars and bottom-axis labels bound to the normalized list**

Target shape:
```kotlin
LaunchedEffect(stats) {
    modelProducer.runTransaction {
        columnSeries {
            series(stats.map { it.count })
        }
    }
}

bottomAxis = rememberBottomAxis(
    valueFormatter = { x, _, _ ->
        stats.getOrNull(x.toInt())?.date?.substring(5) ?: ""
    }
)
```

The main rule here is not to rebuild an alternate label list or reverse only one side of the UI.

- [ ] **Step 3: Keep the detail list aligned with the same normalized stats**

Use:
```kotlin
items(stats.reversed()) { stat ->
    Text(text = stat.date)
    Text(text = "${stat.count} 次触发")
}
```

This is acceptable because the chart remains oldest-to-newest while the detail list remains newest-first, but both must derive from the same normalized source.

- [ ] **Step 4: Verify the page still handles empty data correctly**

Expected behavior:
- If all 14 days are zero and the DAO returns no rows, the page should still show a 14-day zero series if product wants an always-visible chart.
- If product wants the empty placeholder instead, add one explicit boolean in the VM such as `hasAnyStatsFlow` and keep the current empty-state behavior.

Recommended choice for this fix: keep the current empty placeholder when there is no data at all, because it is a smaller behavior change.

- [ ] **Step 5: Commit the UI update**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt
git commit -m "fix: render action log chart with continuous daily stats"
```

---

### Task 5: Add a ViewModel-facing regression test for the exact user-visible contract

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/ui/ActionLogChartContractTest.kt`

- [ ] **Step 1: Write a small contract test for the 14-day output shape**

```kotlin
package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.DailyStat
import li.songe.gkd.sdp.util.ActionLogStatsPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActionLogChartContractTest {
    @Test
    fun normalizedChartSeriesAlwaysContainsFourteenOrderedDays() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val stats = ActionLogStatsPolicy.normalizeDailyStats(
            rawStats = listOf(DailyStat("2026-04-08", 4)),
            now = now,
            days = 14,
            zoneId = zoneId
        )

        assertEquals(14, stats.size)
        assertEquals("2026-03-26", stats.first().date)
        assertEquals("2026-04-08", stats.last().date)
        assertEquals(4, stats.last().count)
    }
}
```

- [ ] **Step 2: Run the focused chart tests**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*ActionLog*Test"
```

Expected: PASS.

- [ ] **Step 3: Commit the contract test**

```powershell
git add app/src/test/kotlin/li/songe/gkd/sdp/ui/ActionLogChartContractTest.kt
git commit -m "test: lock action log chart output contract"
```

---

### Task 6: Full verification before completion

**Files:**
- No new files

- [ ] **Step 1: Run the focused chart-related unit tests**

```powershell
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*ActionLogStatsPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*ActionLogChartContractTest"
```

Expected: both commands `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run a broader UI/unit safety pass**

```powershell
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*ActionLog*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual verification on device or emulator**

Check:
1. Open `触发记录` and switch to `统计图表`.
2. Confirm the chart covers 14 local calendar days, not a rolling 14x24h slice.
3. Confirm dates with no triggers appear in the detail list as `0 次触发` if there is any data in-window.
4. Confirm `subsId`-filtered and `appId`-filtered pages still show consistent chart and detail counts.
5. Confirm the record list tab behavior is unchanged.

- [ ] **Step 4: Report exact command outcomes**

Do not claim completion without quoting the exact Gradle command results and any residual manual-verification gaps.

