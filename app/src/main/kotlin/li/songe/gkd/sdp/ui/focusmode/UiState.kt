@file:JvmName("FocusModeUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession

@Immutable
data class FocusModeUiState(
    val allRules: List<FocusRule> = emptyList(),
    val activeSession: FocusSession? = null,
    val isActive: Boolean = false,
    val currentWhitelist: List<String> = emptyList(),
    val showRuleEditor: Boolean = false,
    val editingRule: FocusRule? = null,
    val ruleName: String = "",
    val ruleType: Int = FocusRule.RULE_TYPE_QUICK_START,
    val ruleStartTime: String = "22:00",
    val ruleEndTime: String = "23:00",
    val ruleDaysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val ruleDurationHours: Int = 0,
    val ruleDurationMinutes: Int = 30,
    val ruleTotalDurationMinutes: Int = 30,
    val ruleWhitelistApps: List<String> = emptyList(),
    val ruleInterceptMessage: String = "",
    val ruleIsLocked: Boolean = false,
    val ruleLockDurationMinutes: Int = 30,
    val manualHours: Int = 0,
    val manualMinutes: Int = 30,
    val manualTotalDurationMinutes: Int = 30,
    val manualWhitelistApps: List<String> = emptyList(),
    val manualMessage: String = "",
    val manualIsLocked: Boolean = false,
    val manualLockDurationMinutes: Int = 30,
    val selectedLockDuration: Int = 480,
    val isCustomLockDuration: Boolean = false,
    val customLockDaysText: String = "",
    val customLockHoursText: String = "",
    val whitelistSearchQuery: String = "",
    val showSystemAppsInWhitelist: Boolean = false,
)

sealed interface FocusModeAction {
    data object OpenRuleEditor : FocusModeAction
    data object CloseRuleEditor : FocusModeAction
    data class EditRule(val rule: FocusRule) : FocusModeAction
    data class ToggleRuleEnabled(val rule: FocusRule) : FocusModeAction
    data class DeleteRule(val rule: FocusRule) : FocusModeAction
    data class LockRule(val rule: FocusRule) : FocusModeAction
    data class StartQuickRule(val rule: FocusRule) : FocusModeAction
    data object StartManualSession : FocusModeAction
    data object StopManualSession : FocusModeAction
}
