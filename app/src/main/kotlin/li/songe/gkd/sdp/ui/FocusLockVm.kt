package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.FocusLock
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.data.ResolvedGroup
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.toast

data class GroupState(
    val group: ResolvedGroup,
    val isInterceptEnabled: Boolean,
    val isSelectedForLock: Boolean
)

class FocusLockVm : BaseViewModel() {
    private val selectedRulesSetFlow = MutableStateFlow<Set<FocusLock.LockedRule>>(emptySet())
    var selectedDuration by mutableIntStateOf(480)
    var isCustomDuration by mutableStateOf(false)
    var customDaysText by mutableStateOf("")
    var customHoursText by mutableStateOf("")

    val groupStatesFlow: StateFlow<List<GroupState>> = combine(
        ruleSummaryFlow,
        DbSet.interceptConfigDao.queryAll(),
        selectedRulesSetFlow
    ) { summary, interceptConfigs, selectedRules ->
        val enabledAppGroups = summary.appIdToAllGroups.values.flatten().filter { it.enable }
        val enabledGlobalGroups = summary.globalGroups
        val allGroups = enabledAppGroups + enabledGlobalGroups

        allGroups.map { group ->
            val ruleKey = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
            GroupState(
                group = group,
                isInterceptEnabled = interceptConfigs.any { it.subsId == group.subsItem.id && it.groupKey == group.group.key && it.enabled },
                isSelectedForLock = selectedRules.contains(ruleKey)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleRuleSelection(group: ResolvedGroup) {
        val rule = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
        val current = selectedRulesSetFlow.value
        selectedRulesSetFlow.value = if (current.contains(rule)) {
            current - rule
        } else {
            current + rule
        }
    }

    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            val allRules = groupStatesFlow.value.map {
                FocusLock.LockedRule(it.group.subsItem.id, it.group.group.key, it.group.appId)
            }.toSet()
            selectedRulesSetFlow.value = allRules
        } else {
            selectedRulesSetFlow.value = emptySet()
        }
    }

    fun toggleIntercept(group: ResolvedGroup) = viewModelScope.launch(Dispatchers.IO) {
        val currentEnabled = groupStatesFlow.value.find { it.group == group }?.isInterceptEnabled ?: false
        val newEnabled = !currentEnabled
        val config = InterceptConfig(
            subsId = group.subsItem.id,
            groupKey = group.group.key,
            enabled = newEnabled
        )
        DbSet.interceptConfigDao.insert(config)
    }

    fun startLock() = viewModelScope.launchTry(Dispatchers.IO) {
        val rules = selectedRulesSetFlow.value.toList()
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
        selectedRulesSetFlow.value = emptySet()
    }
}