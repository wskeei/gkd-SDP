@file:JvmName("FocusModePresenter0")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession

fun FocusModeUiState.reduce(action: FocusModeAction): FocusModeUiState = when (action) {
    FocusModeAction.OpenRuleEditor -> copy(showRuleEditor = true)
    FocusModeAction.CloseRuleEditor -> copy(
        showRuleEditor = false,
        editingRule = null,
    )
    is FocusModeAction.EditRule -> copy(
        editingRule = action.rule,
        showRuleEditor = true,
    )
    else -> this
}

fun FocusModeVm.present(
    allRules: List<FocusRule>,
    activeSession: FocusSession?,
    isActive: Boolean,
    currentWhitelist: List<String>,
): FocusModeUiState = FocusModeUiState(
    allRules = allRules,
    activeSession = activeSession,
    isActive = isActive,
    currentWhitelist = currentWhitelist,
    showRuleEditor = showRuleEditor,
    editingRule = editingRule,
    ruleName = ruleName,
    ruleType = ruleType,
    ruleStartTime = ruleStartTime,
    ruleEndTime = ruleEndTime,
    ruleDaysOfWeek = ruleDaysOfWeek,
    ruleDurationHours = ruleDurationHours,
    ruleDurationMinutes = ruleDurationMinutes,
    ruleTotalDurationMinutes = ruleTotalDurationMinutes,
    ruleWhitelistApps = ruleWhitelistApps,
    ruleInterceptMessage = ruleInterceptMessage,
    ruleIsLocked = ruleIsLocked,
    ruleLockDurationMinutes = ruleLockDurationMinutes,
    manualHours = manualHours,
    manualMinutes = manualMinutes,
    manualTotalDurationMinutes = totalDurationMinutes,
    manualWhitelistApps = manualWhitelistApps,
    manualMessage = manualMessage,
    manualIsLocked = manualIsLocked,
    manualLockDurationMinutes = manualLockDurationMinutes,
    selectedLockDuration = selectedLockDuration,
    isCustomLockDuration = isCustomLockDuration,
    customLockDaysText = customLockDaysText,
    customLockHoursText = customLockHoursText,
    whitelistSearchQuery = whitelistSearchQuery,
    showSystemAppsInWhitelist = showSystemAppsInWhitelist,
)
