# Plan: Fix App Group Switch Not Disabled When Locked

## User Issue
In "App Blocker", when an **App Group** is locked, the global enable/disable switch for that group remains interactive. It should be disabled (locked to "On") to prevent users from bypassing the lock by simply turning off the group.

## Analysis
1.  **Current Behavior**: `AppGroupCard` has a `Switch`. Its `enabled` parameter is likely not checking `group.isCurrentlyLocked`.
2.  **Target Behavior**: If `group.isCurrentlyLocked` is true, the `Switch` should be disabled (greyed out) and locked to the `checked=true` state.
    *   *Note*: The ViewModel (`AppBlockerVm.toggleGroupEnabled`) might already have a check, but visual feedback is missing. Or maybe the VM check is also missing/flawed. User says "it is still possible to close and open", which implies the action works.

## Implementation Steps

### Step 1: UI Fix (`AppBlockerPage.kt`)
1.  **Modify `AppGroupCard`**:
    *   Find the `Switch` component for the group header.
    *   Update `enabled` parameter: `enabled = !group.isCurrentlyLocked`.
    *   (Optional but good): Ensure `checked` forces to `true` if locked? No, the data should already be true because locking sets `enabled=true`. Just disabling interaction is enough.

### Step 2: Logic Verification (`AppBlockerVm.kt`)
1.  **Check `toggleGroupEnabled`**:
    *   Verify it has a guard clause: `if (group.isCurrentlyLocked) return`.
    *   If missing or incorrect, fix it. The user says "it is still possible", so the guard might be missing or the UI isn't reflecting the state.

## Detailed Tasks

1.  **Read `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt`** to locate `AppGroupCard`.
2.  **Read `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`** to check `toggleGroupEnabled`.
3.  **Apply Fixes**.

