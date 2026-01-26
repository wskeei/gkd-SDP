# Plan: Fix App Install Monitor Heatmap Logic

## Problem
The user reports that uninstalled apps (e.g., installed and uninstalled on Jan 22) still appear as "present" in the heatmap and daily details for subsequent days (Jan 23, 24, etc.).

## Root Cause Analysis
The current logic in `AppInstallMonitorVm.kt` calculates "presence" by pairing *any* installation with the *most recent* uninstallation found in the logs.
Specifically:
1.  It iterates over days.
2.  It finds logs for an app.
3.  It checks if `installTime <= endOfDay`.
4.  It finds an `uninstallTime` (if any).
5.  **The Bug**: It uses `logs.find { it.action == "uninstall" ... }`. Since `logs` are often sorted by timestamp DESC or just queried broadly, this `find` might pick an uninstall from a *later* cycle (e.g., re-installed next week), or it fails to correctly pair the *specific* uninstall event that closed the *specific* install event.
6.  For the user's case: Installed Jan 22, Uninstalled Jan 22. On Jan 23, the logic sees "Installed on Jan 22" (true) and "Uninstalled on Jan 22" (true). It should conclude "Not present on Jan 23".
    *   If the logic is `uninstallTime == null || uninstallTime > startOfDay`, then Jan 23 start (00:00) < Jan 22 uninstall (e.g. 14:00) is FALSE. Wait.
    *   Correct logic for "Present on Day D":
        *   `InstallTime < EndOfDay(D)`
        *   AND
        *   `(UninstallTime IS NULL OR UninstallTime > StartOfDay(D))`
    *   Let's check the trace. The trace says: "It uses logs.find ... on a list sorted by timestamp DESC. This retrieves the *most recent* uninstallation record...".
    *   If the user *only* installed/uninstalled on Jan 22, there is only one install and one uninstall.
    *   Why would it show on Jan 23?
        *   Install: Jan 22 10:00.
        *   Uninstall: Jan 22 11:00.
        *   Target: Jan 23.
        *   `Install (22nd) < EndOfJan23` -> True.
        *   `Uninstall (22nd) > StartOfJan23` -> **False**. (11:00 Jan 22 is NOT greater than 00:00 Jan 23).
        *   So it should return **False** (Not present).
    *   If it returns True, the logic must be ignoring the uninstall time or comparing it wrong.
    *   **Hypothesis**: The code might be doing `uninstallTime > EndOfDay`? Or maybe it can't find the uninstall log effectively?

    *   **Alternative Logic (The Fix)**:
        Instead of complex pairing, we can use a simpler "Snapshot" approach for each day:
        1.  Get all logs for a package up to `EndOfDay(D)`.
        2.  Sort by time descending.
        3.  Look at the **very last** log (most recent).
        4.  If it is `install`, the app is present.
        5.  If it is `uninstall`, the app is not present.
        *   *Exception*: If the app was installed AND uninstalled *within* Day D, it *was* present on Day D (at least for a while).
        *   So: "Present on Day D" = "Was installed at start of day" OR "Was installed during the day".
        *   "Was installed at start of day": Last log before `StartOfDay(D)` was `install`.
        *   "Was installed during the day": There exists an `install` log between `StartOfDay(D)` and `EndOfDay(D)`.

## Implementation Plan

### Step 1: Update `AppInstallMonitorVm.kt`
1.  **Modify `calculateHeatmapData`** (and `loadLogsForDate` if it uses shared logic).
2.  **Algorithm**:
    *   Fetch *all* logs (or sufficient history). Group by `packageName`.
    *   For each day `D` in the heatmap range:
        *   For each package `P`:
            *   Get logs for `P` where `timestamp <= EndOfDay(D)`.
            *   Sort by `timestamp ASC`.
            *   Check if present:
                *   **Condition A**: Last log before `StartOfDay(D)` was `install`. (Carried over from previous day).
                *   **Condition B**: There is an `install` log *on* Day D (timestamp between start/end).
            *   If A or B is true, count it.

    *   *Optimization*: Iterate through sorted logs once to build state.
        *   Map `packageName -> isInstalled` (boolean).
        *   Iterate days from past to future.
        *   For each day, process logs *of that day*.
            *   If `install`: set `isInstalled = true`, mark "present today".
            *   If `uninstall`: set `isInstalled = false`.
            *   If `isInstalled` was true at start of day, mark "present today".
        *   This O(N) approach is much faster and more accurate than O(Days * Logs) queries.

### Step 2: Fix `refreshInstalledStatus`
*   The investigator noted it only back-fills installs. It should also back-fill uninstalls?
*   Actually, if an app is NOT installed now, but the last log says "Install", we missed an uninstall event. We should insert an "Uninstall" log (timestamp = now? or estimated?). "Now" is safest.

## Detailed Tasks

1.  **Read `AppInstallMonitorVm.kt`** to see current implementation.
2.  **Refactor `calculateHeatmapData`** to use the "Replay State" algorithm.
    *   Sort all logs by time ASC.
    *   Group logs by Day.
    *   Maintain `currentInstalledApps` set.
    *   Iterate days range.
        *   Start count = `currentInstalledApps.size`.
        *   Process today's logs:
            *   Install: Add to set. If not already in set (re-install same day?), count doesn't change (it's already present). Wait, if installed *during* day, it contributes to "presence".
            *   Uninstall: Remove from set.
        *   Actually, "Daily Presence" usually means "did it exist at all?".
        *   Set `activeAppsToday = currentInstalledApps.clone()`.
        *   For log in today's logs:
            *   If `install`: `activeAppsToday.add(pkg)`. `currentInstalledApps.add(pkg)`.
            *   If `uninstall`: `currentInstalledApps.remove(pkg)`. (Do not remove from `activeAppsToday` because it WAS active).
        *   `heatmapData[day] = activeAppsToday.size`.

3.  **Refactor `loadLogsForDate`** similarly to show correct list.
    *   For a specific date `D`:
        *   Find state at `StartOfDay(D)` (Replay all logs up to that point).
        *   Include any app installed *during* `D`.

4.  **Verify**:
    *   Check the user's case:
        *   Jan 22 Install -> `current` adds. `active` adds.
        *   Jan 22 Uninstall -> `current` removes. `active` keeps.
        *   Result Jan 22: Count 1. Correct.
        *   Jan 23:
        *   Start with `current` (empty).
        *   No logs.
        *   Result Jan 23: Count 0. Correct.

