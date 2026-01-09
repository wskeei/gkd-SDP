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
import li.songe.gkd.sdp.util.json

data class GroupState(
    val group: ResolvedGroup,
    val isInterceptEnabled: Boolean,
    val isSelectedForLock: Boolean,
    val isAlreadyLocked: Boolean
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
        selectedRulesSetFlow,
        FocusLockUtils.activeLockFlow
    ) { summary, interceptConfigs, selectedRules, activeLock ->
        val enabledAppGroups = summary.appIdToAllGroups.values.flatten().filter { it.enable }
        val enabledGlobalGroups = summary.globalGroups
        val allGroups = enabledAppGroups + enabledGlobalGroups

        val lockedRules = if (activeLock != null && activeLock.isActive) {
            json.decodeFromString<List<FocusLock.LockedRule>>(activeLock.lockedRules).toSet()
        } else {
            emptySet()
        }

        allGroups.map { group ->
            val ruleKey = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
            val isLocked = lockedRules.contains(ruleKey)
            GroupState(
                group = group,
                isInterceptEnabled = interceptConfigs.any { it.subsId == group.subsItem.id && it.groupKey == group.group.key && it.enabled },
                isSelectedForLock = selectedRules.contains(ruleKey),
                isAlreadyLocked = isLocked
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleRuleSelection(group: ResolvedGroup) {
        val rule = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
        val current = selectedRulesSetFlow.value
        // If already locked in active session, prevent toggling (it's effectively always selected)
        if (groupStatesFlow.value.find { it.group == group }?.isAlreadyLocked == true) return

        selectedRulesSetFlow.value = if (current.contains(rule)) {
            current - rule
        } else {
            current + rule
        }
    }

    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            val allRules = groupStatesFlow.value
                .filter { !it.isAlreadyLocked } // Only select those not already locked
                .map {
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

    fun updateOrStartLock() = viewModelScope.launchTry(Dispatchers.IO) {
        val rules = selectedRulesSetFlow.value.toList()
        val duration = if (isCustomDuration) {
            val days = customDaysText.toIntOrNull() ?: 0
            val hours = customHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedDuration
        }

        val activeLock = FocusLockUtils.activeLockFlow.value
        if (activeLock != null && activeLock.isActive) {
            // Update existing lock
            if (rules.isEmpty() && duration <= 0) {
                toast("请选择要添加的规则或设置延长时间")
                return@launchTry
            }
            FocusLockUtils.updateLock(activeLock, rules, duration)
            var msg = "锁定更新成功"
            if (rules.isNotEmpty()) msg += "，添加了 ${rules.size} 个规则"
            if (duration > 0) msg += "，延长了 $duration 分钟"
            toast(msg)
        } else {
            // Start new lock
            if (rules.isEmpty()) {
                toast("请选择要锁定的规则组")
                return@launchTry
            }
            if (duration <= 0) {
                toast("请输入有效的锁定时长")
                return@launchTry
            }
            FocusLockUtils.createLock(rules, duration)
            toast("已锁定 ${rules.size} 个规则组，${duration} 分钟后自动解锁")
        }
        selectedRulesSetFlow.value = emptySet()
    }
}