# Plan: Enable Locking for Individual Url Rules

## User Objective
Enable locking for "Individual Rules" (UrlBlockRule) and ensure "Rule Groups" (UrlRuleGroup) can be locked. The user reports inability to lock them.

## Diagnosis
1.  **Rule Groups**: Code shows locking logic exists. Will verify and ensure UI is accessible.
2.  **Individual Rules**: Currently `UrlBlockRule` entities lack `isLocked`/`lockEndTime` fields. They rely on child `TimeRule`s for locking, or Global lock.
    *   *Gap*: If a user adds a site without time rules (implicit "Always Block"), they currently have no way to lock it.
    *   *Fix*: Promote `UrlBlockRule` to have its own locking capability, similar to `UrlRuleGroup`.

## Implementation Steps

### Step 1: Data Layer Update
1.  **Modify `UrlBlockRule.kt`**:
    *   Add `isLocked: Boolean` (default false).
    *   Add `lockEndTime: Long` (default 0).
    *   Add helper `isCurrentlyLocked`.
2.  **Migration**:
    *   Update `AppDb.kt`: Version 28 -> 29.
    *   Add AutoMigration.

### Step 2: ViewModel Update (`UrlBlockVm.kt`)
1.  **Update `saveUrlRule`**: Preserve `isLocked` and `lockEndTime` when updating.
2.  **Add `lockUrlRule(rule, duration)`**: Logic to update DB with new lock time.
3.  **Update Checks**:
    *   `deleteUrlRule`: Check `rule.isCurrentlyLocked`.
    *   `toggleUrlRuleEnabled`: Check `rule.isCurrentlyLocked`.

### Step 3: UI Update (`UrlBlockerComponents.kt`)
1.  **Update `UrlItemCard`**:
    *   Add "Lock" button (icon).
    *   Display lock status (Red lock icon + countdown).
    *   Hide "Delete" button when locked.
    *   Pass `onLock` callback.

### Step 4: Integration (`UrlBlockPage.kt`)
1.  **State**: Add `lockTargetUrlRule` state.
2.  **Sheet**: Add `UrlLockSheet` instance for `UrlBlockRule`.
3.  **Wiring**: Connect `UrlItemCard.onLock` to show the sheet.

## Verification
*   Compile check.
*   Logic verify: Locking a URL rule should prevent deletion and toggling.

