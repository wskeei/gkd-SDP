@file:JvmName("AppBlockerUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule

@Immutable
data class AppBlockerUiState(
    val allGroups: List<AppGroup> = emptyList(),
    val allRules: List<BlockTimeRule> = emptyList(),
    val globalLock: AppBlockerLock? = null,
    val showGroupEditor: Boolean = false,
    val showRuleEditor: Boolean = false,
    val editingGroup: AppGroup? = null,
    val editingRule: BlockTimeRule? = null,
    val groupName: String = "",
    val groupApps: List<String> = emptyList(),
    val groupEditorMode: AppBlockerGroupEditorMode = AppBlockerGroupEditorMode.Create,
    val ruleTargetType: Int = BlockTimeRule.TARGET_TYPE_APP,
    val ruleTargetId: String = "",
    val ruleStartTime: String = "22:00",
    val ruleEndTime: String = "08:00",
    val ruleDaysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val ruleInterceptMessage: String = "",
    val ruleIsAllowMode: Boolean = false,
    val selectedLockDuration: Int = 480,
    val isCustomLockDuration: Boolean = false,
    val customLockDaysText: String = "",
    val customLockHoursText: String = "",
)

enum class AppBlockerGroupEditorMode {
    Create,
    Edit,
    AppendApps,
}

sealed interface AppBlockerAction {
    data object OpenGroupEditor : AppBlockerAction
    data object CloseGroupEditor : AppBlockerAction
    data class EditGroup(val group: AppGroup) : AppBlockerAction
    data object OpenRuleEditor : AppBlockerAction
    data object CloseRuleEditor : AppBlockerAction
    data class EditRule(val rule: BlockTimeRule) : AppBlockerAction
    data class ToggleGroupEnabled(val group: AppGroup) : AppBlockerAction
    data class DeleteGroup(val group: AppGroup) : AppBlockerAction
    data class ToggleRuleEnabled(val rule: BlockTimeRule) : AppBlockerAction
    data class DeleteRule(val rule: BlockTimeRule) : AppBlockerAction
    data object LockGlobal : AppBlockerAction
    data class LockGroup(val group: AppGroup) : AppBlockerAction
    data class LockRule(val rule: BlockTimeRule) : AppBlockerAction
}
