# App Blocker Group Fixes Code Review Round 2

Reviewed worktree: `D:\Project\gkd-SDP\.worktrees\app-blocker-group-fixes`

Reviewed range: `b6e0e34d..767727db`

## Findings

### 1. Minor: fixing the top-edge bounce now also disables normal swipe-to-dismiss whenever the sheet is at the top

Files:
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt:17-21`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:793-809`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt:948-967`

`canDragSheet(...)` now returns `true` only after the inner content has already scrolled away from the top. That value is wired directly into `ModalBottomSheet(sheetGesturesEnabled = swipeEnabled)`.

So the bounce repro path is addressed, but the side effect is that:

- both sheets open with drag gestures disabled;
- the rule sheet cannot be swipe-dismissed while the list is at its top position;
- the group editor sheet cannot be swipe-dismissed at all when its content is shorter than the viewport, because `scrollState.value` stays `0`.

This is likely acceptable if fully intentional, but it is still a behavior regression from standard bottom-sheet interaction and is broader than the original bug report.

## Open Questions / Assumptions

- I did not run a device-side manual gesture check, so the finding above is based on the current `sheetGesturesEnabled` wiring rather than a recorded UI session.
- I treated preserving normal bottom-sheet dismissal as desirable because the requirement only asked to stop the upward bounce, not to remove swipe gestures altogether.

## Change Summary

The previous three review findings appear resolved:

- the sheet drag policy is no longer inverted;
- `Edit` mode no longer advertises editable existing selections in the VM test contract;
- `添加规则` is now hidden during global lock, matching `添加应用`.

## Verification

Commands run against the worktree:

- `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest" --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`
- `.\gradlew.bat :app:compileGkdDebugKotlin`

Result:

- Both commands completed successfully.
- I did not find any remaining requirement mismatch from the first review besides the swipe-gesture regression above.

