@file:JvmName("AppBlockerPresenter0")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule

fun AppBlockerUiState.reduce(action: AppBlockerAction): AppBlockerUiState = when (action) {
    AppBlockerAction.OpenGroupEditor -> copy(showGroupEditor = true)
    AppBlockerAction.CloseGroupEditor -> copy(
        showGroupEditor = false,
        editingGroup = null,
    )
    is AppBlockerAction.EditGroup -> copy(
        editingGroup = action.group,
        showGroupEditor = true,
    )
    AppBlockerAction.OpenRuleEditor -> copy(showRuleEditor = true)
    AppBlockerAction.CloseRuleEditor -> copy(
        showRuleEditor = false,
        editingRule = null,
    )
    is AppBlockerAction.EditRule -> copy(
        editingRule = action.rule,
        showRuleEditor = true,
    )
    else -> this
}

fun AppBlockerVm.present(
    allGroups: List<AppGroup>,
    allRules: List<BlockTimeRule>,
    globalLock: AppBlockerLock?,
): AppBlockerUiState = AppBlockerUiState(
    allGroups = allGroups,
    allRules = allRules,
    globalLock = globalLock,
    showGroupEditor = showGroupEditor,
    showRuleEditor = showRuleEditor,
    editingGroup = editingGroup,
    editingRule = editingRule,
    groupName = groupName,
    groupApps = groupApps,
    groupEditorMode = when (groupEditorMode) {
        AppBlockerVm.GroupEditorMode.Create -> AppBlockerGroupEditorMode.Create
        AppBlockerVm.GroupEditorMode.Edit -> AppBlockerGroupEditorMode.Edit
        AppBlockerVm.GroupEditorMode.AppendApps -> AppBlockerGroupEditorMode.AppendApps
    },
    ruleTargetType = ruleTargetType,
    ruleTargetId = ruleTargetId,
    ruleStartTime = ruleStartTime,
    ruleEndTime = ruleEndTime,
    ruleDaysOfWeek = ruleDaysOfWeek,
    ruleInterceptMessage = ruleInterceptMessage,
    ruleIsAllowMode = ruleIsAllowMode,
    selectedLockDuration = selectedLockDuration,
    isCustomLockDuration = isCustomLockDuration,
    customLockDaysText = customLockDaysText,
    customLockHoursText = customLockHoursText,
)
