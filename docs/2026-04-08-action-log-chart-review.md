# Action Log Chart Implementation Review

Review scope: current codebase state versus `docs/plans/2026-04-08-action-log-chart-fix-plan.md`.

## Findings

1. **High: root cause from the plan is still present, so the chart window is still wrong**

   In `ActionLogVm`, `dailyStatsFlow` still queries with a rolling `System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L` cutoff instead of a local-calendar-day-aligned window. This is the exact bug the plan was written to fix. It means the first displayed day is still a partial day and the chart still changes depending on what time the page is opened.

   Evidence:
   - `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogVm.kt:27-31`
   - Planned replacement was `ActionLogStatsPolicy.windowStartEpochMs(...)` in `docs/plans/2026-04-08-action-log-chart-fix-plan.md`

   Why this fails the plan:
   - Task 2 and Task 3 explicitly required a pure helper plus a local-day-aligned `statsWindowStart`.
   - The implementation still uses the old rolling 14x24h logic.

2. **High: the sparse DAO result is still rendered directly, so missing zero-count days are still dropped**

   `ActionLogPage` still renders `stats.map { it.count }` directly into the chart and uses `stats.getOrNull(x.toInt())` for labels. Because `queryDailyStats()` only returns dates that have records, dates with zero triggers still disappear from the series. This leaves the chart compressed and semantically wrong even if the SQL query itself succeeds.

   Evidence:
   - `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt:258-303`
   - `app/src/main/kotlin/li/songe/gkd/sdp/data/ActionLog.kt:133-147`

   Why this fails the plan:
   - Task 2 required `normalizeDailyStats(...)` to expand sparse rows into a fixed continuous day series.
   - Task 4 required chart bars, axis labels, and detail rows to consume that normalized dataset.
   - No normalization layer exists in the current code.

3. **Medium: the required implementation files from the plan were not created**

   The plan required a dedicated policy/helper file and two test files:
   - `app/src/main/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicy.kt`
   - `app/src/test/kotlin/li/songe/gkd/sdp/util/ActionLogStatsPolicyTest.kt`
   - `app/src/test/kotlin/li/songe/gkd/sdp/ui/ActionLogChartContractTest.kt`

   All three are absent in the current worktree.

   Why this fails the plan:
   - Task 1, Task 2, and Task 5 were explicitly built around these files.
   - Their absence means the fix was not implemented using the agreed architecture, and the critical behaviors are still untested.

4. **Medium: the UI text still advertises the old, misleading semantics**

   The chart title is still `"触发趋势 (最近14天)"`, not the planned `"触发趋势（最近14个自然日）"`. That is not just copy drift. It reflects that the implementation never switched from a rolling 14x24h window to the intended local-calendar-day model.

   Evidence:
   - `app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt:285`

   Why this fails the plan:
   - Task 4 Step 1 explicitly required the copy change after the data semantics were corrected.

5. **Medium: there is no regression protection for the exact user-visible contract**

   No focused tests exist to lock the following behaviors:
   - local-day-aligned 14-day window
   - zero-filled missing days
   - stable oldest-to-newest normalized chart series
   - chart/detail list staying on the same normalized data source

   Why this matters:
   - The original bug remains possible because there is no test guarding the intended contract.
   - The plan explicitly required RED/GREEN coverage before and after the implementation.

## Overall Assessment

The reviewed code does not satisfy the implementation plan. The planned repair architecture was not introduced, the original root cause in `ActionLogVm` remains, and the chart still consumes sparse per-day data directly. From a review standpoint, this should be treated as **not implemented** rather than “implemented with some minor deviations.”

## Recommended Next Step

Execute the plan as written, starting with the missing tests and the missing `ActionLogStatsPolicy` helper. Do not adjust the UI first. The current issue is upstream in the date-window and normalization logic, and the UI is only reflecting that unresolved backend/viewmodel behavior.

