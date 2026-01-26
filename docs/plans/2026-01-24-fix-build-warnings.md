# Plan: Fix Build Warnings

## User Objective
Fix deprecated API usage warnings found in the recent build.

## Analysis
1.  **`AntiUninstallPage.kt`**: `LocalLifecycleOwner` is deprecated in `androidx.compose.ui.platform`.
    *   **Fix**: Import `androidx.lifecycle.compose.LocalLifecycleOwner` instead.
2.  **`AppInstallMonitorPage.kt`**: `TabRow` is deprecated in Material3.
    *   **Fix**: Replace with `PrimaryTabRow` (since it's a main tab navigation).
3.  **`FocusQuickStartWidget.kt`**:
    *   `setRemoteAdapter(int, Intent)`: Deprecated in API 31.
    *   `notifyAppWidgetViewDataChanged(int, int)`: Deprecated? Actually `notifyAppWidgetViewDataChanged` usually takes `int[]` appWidgetIds.
    *   **Fix**: Suppress deprecation for `setRemoteAdapter` (as we support API 26). Ensure `notifyAppWidgetViewDataChanged` uses the array overload `notifyAppWidgetViewDataChanged(intArrayOf(id), viewId)`.

## Implementation Steps

### Step 1: Fix `AntiUninstallPage.kt`
1.  Read file.
2.  Change import `androidx.compose.ui.platform.LocalLifecycleOwner` to `androidx.lifecycle.compose.LocalLifecycleOwner`.

### Step 2: Fix `AppInstallMonitorPage.kt`
1.  Read file.
2.  Replace `TabRow` with `PrimaryTabRow`. The API is similar.

### Step 3: Fix `FocusQuickStartWidget.kt`
1.  Read file.
2.  Add `@Suppress("DEPRECATION")` to `setRemoteAdapter`.
3.  Check `notifyAppWidgetViewDataChanged`. If it uses single int, wrap it in `intArrayOf`.

## Detailed Tasks
1.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/ui/AntiUninstallPage.kt`.
2.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppInstallMonitorPage.kt`.
3.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt`.

