# App Blocker Group Fixes Code Review

Reviewed worktree: `D:\Project\gkd-SDP\.worktrees\app-blocker-group-fixes`

Reviewed range: `3b34060027c4e906f4978b0d0f5b187b1f7eaaf3..b6e0e34d`

## Findings

### 1. Important: the reported “上滑后整页弹跳” condition is still enabled by the new guard

Files:
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt:14-21`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:955-967`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:800-809`

`canDragSheet(...)` returns `true` exactly when the inner content is already at the top (`index == 0 && offset == 0`). That is the same state in which the user reported the bug: opening the rule sheet and swiping upward causes the whole sheet to spring/bounce. The current wiring therefore still leaves the sheet draggable in the reported scenario, so this change guards some non-top interactions but does not actually close the original bug condition.

In other words, the implementation currently blocks sheet state changes only after the inner list/scroll has moved away from the top. It does not suppress the top-of-sheet upward drag that the user asked to fix.

### 2. Medium: the new VM contract and test encode a behavior that conflicts with the requirement that existing apps must not become removable

Files:
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt:367-379`
- `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmGroupEditorTest.kt:18-27`

The user requirement was explicit: for an existing app group, already-added apps cannot be removed; removal should only happen by deleting the whole group. However `buildGroupPickerConfig(..., GroupEditorMode.Edit)` still returns the full current selection with no exclusions, and the corresponding test is named `editModeKeepsCurrentSelectionEditable`.

The UI currently avoids exposing that picker in normal edit mode, so the shipped behavior is safer than the VM contract. But the tested contract is still wrong. Any later UI refactor that reuses `buildGroupPickerConfig(Edit)` will immediately re-open the forbidden “edit existing membership” path while the unit test keeps passing. This is a requirement mismatch encoded into the abstraction itself.

### 3. Minor: `添加规则` still exposes a dead-end action during global lock while `添加应用` is correctly hidden

Files:
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:558-572`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:302-316`

The new `添加应用` button is hidden when `globalLock?.isCurrentlyLocked == true`, but `添加规则` still only checks `!group.isCurrentlyLocked`. Under a global lock, the group card therefore still offers `添加规则`, opens the sheet, and then renders it read-only because `isLocked` is derived from the global lock at the page level.

That leaves a visible action that cannot complete, which is inconsistent with the new add-app flow and weakens the locked-state UX.

## Open Questions / Assumptions

- I did not run an instrumented gesture test on a device or emulator, so Finding 1 is based on the current drag-gating logic and the reported repro path, not on a captured UI session.
- I treated the user’s statement “已经添加的应用，不能去除” as applying to all persisted group membership, which makes the `Edit` picker contract in Finding 2 a design mismatch even though the current UI hides that route.

## Verification

Commands run against the worktree:

- `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest" --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`
- `.\gradlew.bat :app:compileGkdDebugKotlin`

Result:

- Both commands completed successfully.
- The branch compiles and the focused unit tests pass.
- The issues above are therefore review findings about behavior/contract quality, not build-break failures.

