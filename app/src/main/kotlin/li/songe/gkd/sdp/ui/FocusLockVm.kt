package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.songe.gkd.sdp.data.FocusLock
import li.songe.gkd.sdp.data.ResolvedGroup
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.toast

class FocusLockVm : BaseViewModel() {
    val selectedRulesFlow = MutableStateFlow<Set<FocusLock.LockedRule>>(emptySet())
    var selectedDuration by mutableIntStateOf(480)
    var isCustomDuration by mutableStateOf(false)
    var customDaysText by mutableStateOf("")
    var customHoursText by mutableStateOf("")

    val lockableGroupsFlow: StateFlow<List<ResolvedGroup>> = ruleSummaryFlow.map { summary ->
        val enabledAppGroups = summary.appIdToAllGroups.values.flatten().filter { it.enable }
        val enabledGlobalGroups = summary.globalGroups
        enabledAppGroups + enabledGlobalGroups
    }.stateInit(emptyList())

    fun toggleRule(rule: FocusLock.LockedRule) {
        selectedRulesFlow.value = if (selectedRulesFlow.value.contains(rule)) {
            selectedRulesFlow.value - rule
        } else {
            selectedRulesFlow.value + rule
        }
    }

    fun startLock() = viewModelScope.launchTry(Dispatchers.IO) {
        val rules = selectedRulesFlow.value.toList()
        if (rules.isEmpty()) {
            toast("请选择要锁定的规则组")
            return@launchTry
        }

        val duration = if (isCustomDuration) {
            val days = customDaysText.toIntOrNull() ?: 0
            val hours = customHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedDuration
        }

        if (duration <= 0) {
            toast("请输入有效的锁定时长")
            return@launchTry
        }

        FocusLockUtils.createLock(rules, duration)
        toast("已锁定 ${rules.size} 个规则组，${duration} 分钟后自动解锁")
        selectedRulesFlow.value = emptySet()
    }
}