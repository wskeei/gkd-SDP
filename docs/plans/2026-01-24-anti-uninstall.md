# Plan: Implement Anti-Uninstall Feature (Device Admin + A11y Guard)

## User Objective
Prevent the app from being uninstalled by enabling "Device Admin" mode.
Include a "Lock" feature (8h, 1d, 3d, custom) that prevents the user from deactivating Device Admin during the lock period.
The mechanism must be robust: if locked, A11y service should aggressively prevent access to the "Device Admin" settings page to stop deactivation.
No ADB backdoor. Updates must still be possible (Device Admin allows updates).

## Architecture

### 1. Infrastructure (Device Admin)
*   **Receiver**: `li.songe.gkd.sdp.receiver.AdminReceiver` (extends `DeviceAdminReceiver`).
*   **Policy XML**: `res/xml/device_admin_policies.xml` (force-lock).
*   **Manifest**: Register receiver with `BIND_DEVICE_ADMIN`.

### 2. Data & State
*   **Lock State**: Use existing `ConstraintConfig`.
    *   Add `ConstraintConfig.TYPE_ANTI_UNINSTALL = 2`.
    *   Store lock end time in `ConstraintConfig`.
*   **Helper**: `FocusLockUtils.isAntiUninstallLocked()` to check if locked.

### 3. Logic (A11y Guard)
*   **`AntiUninstallEngine`**:
    *   Listen to `TYPE_WINDOW_STATE_CHANGED`.
    *   Detect if top activity is `com.android.settings` AND contains "DeviceAdmin" (exact classes vary by ROM, so matching "DeviceAdmin" string in class name is a good heuristic, or specific known activities).
    *   **Crucial**: Check if user is trying to *deactivate GKD*.
    *   **Action**: If locked -> `performGlobalAction(GLOBAL_ACTION_BACK)` immediately + Toast.

### 4. UI (`AntiUninstallPage`)
*   **New Page**: `AntiUninstallPage.kt`.
*   **Entry**: Add card in `FocusLockPage.kt` (or wherever "Digital Detox" features are).
*   **Content**:
    *   Status: "Active" / "Inactive".
    *   Switch: Toggle activation (Redirects to system intent).
    *   Lock Button: Shows `LockSheet`.
    *   Logic:
        *   If not active -> Click -> `ACTION_ADD_DEVICE_ADMIN`.
        *   If active & not locked -> Click -> Show "Go to settings to deactivate" or `removeActiveAdmin`.
        *   If locked -> Disable deactivation interactions.

## Implementation Steps

### Step 1: Infrastructure (The Foundation)
1.  Create `app/src/main/res/xml/device_admin_policies.xml`.
2.  Create `app/src/main/kotlin/li/songe/gkd/sdp/receiver/AdminReceiver.kt`.
3.  Modify `app/src/main/AndroidManifest.xml` to register it.

### Step 2: Logic & State
1.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/data/ConstraintConfig.kt`: Add `TYPE_ANTI_UNINSTALL`.
2.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/util/FocusLockUtils.kt`: Add `isAntiUninstallLocked`.
3.  Create `app/src/main/kotlin/li/songe/gkd/sdp/a11y/AntiUninstallEngine.kt`: Implement the guard logic.
4.  Modify `app/src/main/kotlin/li/songe/gkd/sdp/service/A11yService.kt`: Hook up `AntiUninstallEngine.onAccessibilityEvent`.

### Step 3: UI Implementation
1.  Create `app/src/main/kotlin/li/songe/gkd/sdp/ui/AntiUninstallPage.kt` (ViewModel + UI).
2.  Integrate into `FocusLockPage.kt` (Add entry card).

## Verification
1.  Compile.
2.  (Mental Check) Verify `AdminReceiver` is registered correctly (otherwise system settings won't open).
3.  (Mental Check) Verify Engine logic doesn't block *all* settings, only Device Admin.

