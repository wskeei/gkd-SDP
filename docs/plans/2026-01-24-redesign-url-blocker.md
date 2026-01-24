# Plan: Redesign Url Blocker to Match App Blocker Architecture

## User Objective
The user wants to completely redesign the "Url Blocker" feature.
Current state: "Bottom logic is bad", "UI is not intuitive", "Cannot add websites to groups", "Basically unusable".
Desired state: Align UI and logic with "App Blocker" (Group -> Time Rules structure), including the "Three-Layer Locking" mechanism (Global/Group/Rule).

## Architectural Changes

We will replicate the `AppBlocker` architecture for `UrlBlocker`.

### 1. Data Models (Room Entities)

*   **`UrlRuleGroup` (New Entity)**
    *   Replicates `AppGroup`.
    *   Fields: `id`, `name` (e.g., "Video Sites"), `enabled`, `isLocked`, `lockEndTime`, `orderIndex`.
    *   DAO: `UrlRuleGroupDao`.

*   **`UrlBlockRule` (Modify Existing)**
    *   Currently flat list. Needs to belong to a group.
    *   **Add Field**: `groupId: Long` (default 0).
    *   **Logic**: If `groupId > 0`, it belongs to that group. If `0`, it's standalone (or maybe we force everything into groups? *Decision: Align with AppBlocker - AppBlocker has "Standalone Apps" list and "Groups". We will support both.*)
    *   **Note**: `AppBlocker` puts `appIds` JSON list inside `AppGroup`. `UrlBlocker` is slightly different: URLs are individual entities. We will keep `UrlBlockRule` as entities but link them to `UrlRuleGroup` via `groupId`. This is the "Option A" we agreed on.

*   **`UrlTimeRule` (New Entity)**
    *   Replicates `BlockTimeRule`.
    *   Fields: `id`, `targetType` (0=Rule/Website, 1=Group), `targetId` (UrlRuleId or GroupId), `startTime`, `endTime`, `daysOfWeek`, `enabled`, `isLocked`, `lockEndTime`, `isAllowMode`, `interceptMessage`.
    *   DAO: `UrlTimeRuleDao`.

*   **`UrlBlockerLock` (New Entity)**
    *   Replicates `AppBlockerLock` (Singleton).
    *   Fields: `id` (1), `isLocked`, `lockEndTime`.
    *   DAO: `UrlBlockerLockDao`.

### 2. Database Migration
*   Update `AppDb.kt`: Add new entities, update version (already at 27, might need 28 if schemas changed significantly or just use 27 if it was half-baked).
    *   *Correction*: The user context says `AppDb` *already* has `UrlRuleGroup` and `UrlTimeRule` in `entities` list, but the files were missing. I need to create the files matching the schema that might already be expected, or bump version if I change it.
    *   I will verify if I need a migration. Since the files were missing, the compiled schema likely didn't include them effectively or it was broken. I'll treat them as new.

### 3. Business Logic (ViewModel & Engine)

*   **`UrlBlockerVm` (ViewModel)**
    *   Needs to manage Groups, Urls, and Time Rules.
    *   Logic for "Add Group", "Add URL to Group", "Add Time Rule to Group/URL".
    *   Locking logic (Global/Group/Rule).

*   **`UrlBlockerEngine` (Service/Engine)**
    *   Current logic: Checks `UrlBlockRule` directly.
    *   **New Logic**:
        1.  Detect URL visit.
        2.  Find matching `UrlBlockRule`.
        3.  Check if Rule is Enabled.
        4.  **Check Time Rules**:
            *   Find `UrlTimeRule` targeting this Rule (Type 0).
            *   Find `UrlTimeRule` targeting the Rule's Group (Type 1).
            *   If *any* "Prohibit" rule is active -> Block.
            *   If "Allow" mode is used -> complex logic (same as AppBlocker).
    *   *Refinement*: AppBlocker logic is "Rule defines WHEN to block".
        *   If multiple rules apply, prioritizing "Block" is usually safe.
        *   Need to copy `BlockTimeRule.isActiveNow()` logic to `UrlTimeRule`.

### 4. UI (Compose)

*   **`UrlBlockPage`**:
    *   Complete rewrite.
    *   Header: Global Lock status.
    *   Section 1: **Url Groups** (Card style like `AppGroupCard`).
        *   Group Name, Switch, Lock status.
        *   Inside Card: List of Time Rules.
        *   "Edit" opens Group Editor (Name + List of URLs).
    *   Section 2: **Standalone URLs** (Card style like `AppRulesCard`).
        *   URL Pattern, Switch.
        *   Inside Card: List of Time Rules.
    *   **Editors**:
        *   `GroupEditorSheet`: Edit Name, Add/Remove URLs (Pattern input).
        *   `UrlEditorSheet` (New): Edit Pattern, Match Type.
        *   `RuleEditorSheet` (Time Rule): Start/End time, Days, Allow/Block mode.
    *   **LockSheet**: Reused from AppBlocker (copy-paste or refactor to shared if possible, but copy-paste is safer to avoid breaking AppBlocker).

## Implementation Steps

### Step 1: Data Layer (The Foundation)
1.  Create `UrlRuleGroup.kt`.
2.  Create `UrlTimeRule.kt`.
3.  Create `UrlBlockerLock.kt`.
4.  Update `UrlBlockRule.kt` (add `groupId`).
5.  Update `AppDb.kt` (register entities, DAOs).
6.  **Verification**: Compile to ensure Room schema is valid.

### Step 2: ViewModel & Logic
1.  Update `UrlBlockerVm.kt`.
    *   Add Flows for Groups, TimeRules, Lock.
    *   Implement CRUD operations.
    *   Implement Locking logic.

### Step 3: UI - Components
1.  Create `UrlGroupCard` (adapted from `AppGroupCard`).
2.  Create `UrlItemCard` (adapted from `AppRulesCard`).
3.  Create/Update Editors (`UrlGroupEditor`, `UrlRuleEditor`, `TimeRuleEditor`).

### Step 4: UI - Main Page
1.  Rewrite `UrlBlockPage.kt` to assemble the components.
2.  Integrate Locking UI.

### Step 5: Engine Update
1.  Update `UrlBlockerEngine.kt` to respect the new Time Rules and Grouping logic.
    *   It must now query `UrlTimeRule` to decide whether to block.

### Step 6: Cleanup & Verify
1.  Remove old/unused logic.
2.  Manual Verification Checklist (Mental):
    *   Can add group?
    *   Can add URL to group?
    *   Can add time rule to group?
    *   Does time rule block correctly?
    *   Does locking work?

## Q&A / Constraints
*   **Constraint**: "Code must be complete, no omissions." -> I will double-check imports and method bodies.
*   **Constraint**: "Adhere to development principles." -> MVVM, Room best practices, Copying existing successful patterns (AppBlocker).
