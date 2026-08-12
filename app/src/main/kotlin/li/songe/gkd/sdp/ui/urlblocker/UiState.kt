@file:JvmName("UrlBlockerUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule

@Immutable
data class UrlBlockerUiState(
    val allGroups: List<UrlRuleGroup> = emptyList(),
    val allTimeRules: List<UrlTimeRule> = emptyList(),
    val allUrlRules: List<UrlBlockRule> = emptyList(),
    val globalLock: UrlBlockerLock? = null,
    val browsers: List<BrowserConfig> = emptyList(),
    val showGroupEditor: Boolean = false,
    val showUrlEditor: Boolean = false,
    val showTimeRuleEditor: Boolean = false,
    val showBrowserEditor: Boolean = false,
    val showBrowserList: Boolean = false,
    val editingGroup: UrlRuleGroup? = null,
    val editingUrlRule: UrlBlockRule? = null,
    val editingTimeRule: UrlTimeRule? = null,
    val editingBrowser: BrowserConfig? = null,
)

data class UrlRuleDraft(
    val pattern: String,
    val matchType: Int,
    val name: String,
    val redirectUrl: String,
    val showIntercept: Boolean,
    val interceptMessage: String,
    val groupId: Long,
    val timeRuleStartTime: String,
    val timeRuleEndTime: String,
    val timeRuleDaysOfWeek: List<Int>,
    val timeRuleIsAllowMode: Boolean,
)

data class UrlTimeRuleDraft(
    val targetType: Int,
    val targetId: Long,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: List<Int>,
    val isAllowMode: Boolean,
)

data class BrowserDraft(
    val name: String,
    val packageName: String,
    val urlBarId: String,
)

data class UrlLockDraft(
    val durationMinutes: Int,
    val isCustom: Boolean,
    val daysText: String,
    val hoursText: String,
)

sealed interface UrlBlockerAction {
    data object OpenGroupEditor : UrlBlockerAction
    data object CloseGroupEditor : UrlBlockerAction
    data class EditGroup(val group: UrlRuleGroup) : UrlBlockerAction
    data object OpenUrlEditor : UrlBlockerAction
    data object CloseUrlEditor : UrlBlockerAction
    data class EditUrlRule(val rule: UrlBlockRule) : UrlBlockerAction
    data object OpenTimeRuleEditor : UrlBlockerAction
    data object CloseTimeRuleEditor : UrlBlockerAction
    data class EditTimeRule(val rule: UrlTimeRule) : UrlBlockerAction
    data object OpenBrowserEditor : UrlBlockerAction
    data object CloseBrowserEditor : UrlBlockerAction
    data class EditBrowser(val browser: BrowserConfig) : UrlBlockerAction
    data object OpenBrowserList : UrlBlockerAction
    data object CloseBrowserList : UrlBlockerAction
    data class ToggleGroupEnabled(val group: UrlRuleGroup) : UrlBlockerAction
    data class DeleteGroup(val group: UrlRuleGroup) : UrlBlockerAction
    data class ToggleUrlRuleEnabled(val rule: UrlBlockRule) : UrlBlockerAction
    data class DeleteUrlRule(val rule: UrlBlockRule) : UrlBlockerAction
    data class ToggleTimeRuleEnabled(val rule: UrlTimeRule) : UrlBlockerAction
    data class DeleteTimeRule(val rule: UrlTimeRule) : UrlBlockerAction
    data class SaveBrowser(val config: BrowserConfig) : UrlBlockerAction
    data object LockGlobal : UrlBlockerAction
    data class LockGroup(val group: UrlRuleGroup) : UrlBlockerAction
    data class LockUrlRule(val rule: UrlBlockRule) : UrlBlockerAction
    data class LockTimeRule(val rule: UrlTimeRule) : UrlBlockerAction
}
