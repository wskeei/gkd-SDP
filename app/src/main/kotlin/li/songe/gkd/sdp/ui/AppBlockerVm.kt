package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.AppBlockerEngine
import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.toast

class AppBlockerVm : BaseViewModel() {

    val allGroupsFlow = AppBlockerEngine.allGroupsFlow
    val allRulesFlow = AppBlockerEngine.allRulesFlow
    val globalLockFlow = AppBlockerEngine.globalLockFlow

    // 编辑状态
    var editingGroup by mutableStateOf<AppGroup?>(null)
    var editingRule by mutableStateOf<BlockTimeRule?>(null)
    var showGroupEditor by mutableStateOf(false)
    var showRuleEditor by mutableStateOf(false)

    // 应用组表单
    var groupName by mutableStateOf("")
    var groupApps by mutableStateOf<List<String>>(emptyList())

    // 规则表单
    var ruleTargetType by mutableIntStateOf(BlockTimeRule.TARGET_TYPE_APP)
    var ruleTargetId by mutableStateOf("")
    var ruleStartTime by mutableStateOf("22:00")
    var ruleEndTime by mutableStateOf("08:00")
    var ruleDaysOfWeek by mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7))
    var ruleInterceptMessage by mutableStateOf("这真的重要吗？")
    var ruleIsAllowMode by mutableStateOf(false)  // 是否为允许模式（反选）

    // 锁定时长选择
    var selectedLockDuration by mutableIntStateOf(480)  // 默认 8 小时
    var isCustomLockDuration by mutableStateOf(false)
    var customLockDaysText by mutableStateOf("")
    var customLockHoursText by mutableStateOf("")

    fun resetGroupForm() {
        editingGroup = null
        groupName = ""
        groupApps = emptyList()
        showGroupEditor = false
    }

    fun loadGroupForEdit(group: AppGroup) {
        editingGroup = group
        groupName = group.name
        groupApps = group.getAppList()
        showGroupEditor = true
    }

    fun saveGroup() = viewModelScope.launch(Dispatchers.IO) {
        if (groupName.isBlank()) {
            toast("请输入应用组名称")
            return@launch
        }
        if (groupApps.isEmpty()) {
            toast("请至少添加一个应用")
            return@launch
        }

        val group = AppGroup(
            id = editingGroup?.id ?: 0,
            name = groupName.trim(),
            appIds = json.encodeToString(groupApps),
            enabled = editingGroup?.enabled ?: true,
            isLocked = editingGroup?.isLocked ?: false,
            lockEndTime = editingGroup?.lockEndTime ?: 0,
            orderIndex = editingGroup?.orderIndex ?: 0
        )

        DbSet.appGroupDao.insert(group)
        toast(if (editingGroup != null) "应用组已更新" else "应用组已添加")
        resetGroupForm()
    }

    fun deleteGroup(group: AppGroup) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法删除")
            return@launch
        }
        if (group.isCurrentlyLocked) {
            toast("应用组已锁定，无法删除")
            return@launch
        }
        DbSet.appGroupDao.delete(group)
        // 同时删除该应用组的所有规则
        DbSet.blockTimeRuleDao.deleteByTarget(BlockTimeRule.TARGET_TYPE_GROUP, group.id.toString())
        toast("应用组已删除")
    }

    fun toggleGroupEnabled(group: AppGroup) = viewModelScope.launch(Dispatchers.IO) {
        if (group.enabled && (group.isCurrentlyLocked || globalLockFlow.value?.isCurrentlyLocked == true)) {
            toast("应用组已锁定，无法关闭")
            return@launch
        }
        DbSet.appGroupDao.update(group.copy(enabled = !group.enabled))
    }

    fun addAppToGroup(packageName: String) {
        if (!groupApps.contains(packageName)) {
            groupApps = groupApps + packageName
        }
    }

    fun removeAppFromGroup(packageName: String) {
        groupApps = groupApps - packageName
    }

    fun resetRuleForm() {
        editingRule = null
        ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
        ruleTargetId = ""
        ruleStartTime = "22:00"
        ruleEndTime = "08:00"
        ruleDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
        ruleInterceptMessage = "这真的重要吗？"
        ruleIsAllowMode = false
        showRuleEditor = false
    }

    fun loadRuleForEdit(rule: BlockTimeRule) {
        editingRule = rule
        ruleTargetType = rule.targetType
        ruleTargetId = rule.targetId
        ruleStartTime = rule.startTime
        ruleEndTime = rule.endTime
        ruleDaysOfWeek = rule.getDaysOfWeekList()
        ruleInterceptMessage = rule.interceptMessage
        ruleIsAllowMode = rule.isAllowMode
        showRuleEditor = true
    }

    fun applyTemplate(template: BlockTimeRule.Companion.TimeTemplate) {
        ruleStartTime = template.startTime
        ruleEndTime = template.endTime
        ruleDaysOfWeek = template.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun saveRule() = viewModelScope.launch(Dispatchers.IO) {
        if (ruleTargetId.isBlank()) {
            toast("请选择拦截对象")
            return@launch
        }

        val rule = BlockTimeRule(
            id = editingRule?.id ?: 0,
            targetType = ruleTargetType,
            targetId = ruleTargetId,
            startTime = ruleStartTime,
            endTime = ruleEndTime,
            daysOfWeek = ruleDaysOfWeek.joinToString(","),
            enabled = editingRule?.enabled ?: true,
            isLocked = editingRule?.isLocked ?: false,
            lockEndTime = editingRule?.lockEndTime ?: 0,
            createdAt = editingRule?.createdAt ?: System.currentTimeMillis(),
            interceptMessage = ruleInterceptMessage.ifBlank { "这真的重要吗？" },
            isAllowMode = ruleIsAllowMode
        )

        DbSet.blockTimeRuleDao.insert(rule)
        toast(if (editingRule != null) "规则已更新" else "规则已添加")
        resetRuleForm()
    }

    fun deleteRule(rule: BlockTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法删除")
            return@launch
        }
        if (rule.isCurrentlyLocked) {
            toast("规则已锁定，无法删除")
            return@launch
        }
        DbSet.blockTimeRuleDao.delete(rule)
        toast("规则已删除")
    }

    fun toggleRuleEnabled(rule: BlockTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        DbSet.blockTimeRuleDao.update(rule.copy(enabled = !rule.enabled))
    }

    fun lockGlobal() = viewModelScope.launch(Dispatchers.IO) {
        val durationMinutes = if (isCustomLockDuration) {
            val days = customLockDaysText.toIntOrNull() ?: 0
            val hours = customLockHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedLockDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val durationMillis = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val currentLock = globalLockFlow.value
        val currentEndTime = currentLock?.lockEndTime ?: now

        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMillis  // 延长锁定
        } else {
            now + durationMillis  // 新锁定
        }

        val lock = AppBlockerLock(
            id = 1,
            isLocked = true,
            lockEndTime = newEndTime
        )

        DbSet.appBlockerLockDao.insert(lock)
        toast("全局锁定已设置")
    }

    fun lockGroup(group: AppGroup) = viewModelScope.launch(Dispatchers.IO) {
        val durationMinutes = if (isCustomLockDuration) {
            val days = customLockDaysText.toIntOrNull() ?: 0
            val hours = customLockHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedLockDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val durationMillis = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val currentEndTime = if (group.isCurrentlyLocked) group.lockEndTime else now

        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMillis
        } else {
            now + durationMillis
        }

        val updatedGroup = group.copy(
            isLocked = true,
            lockEndTime = newEndTime,
            enabled = true  // 锁定时自动启用
        )

        DbSet.appGroupDao.update(updatedGroup)
        toast("应用组已锁定")
    }

    fun lockRule(rule: BlockTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val durationMinutes = if (isCustomLockDuration) {
            val days = customLockDaysText.toIntOrNull() ?: 0
            val hours = customLockHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedLockDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val durationMillis = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val currentEndTime = if (rule.isCurrentlyLocked) rule.lockEndTime else now

        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMillis
        } else {
            now + durationMillis
        }

        val updatedRule = rule.copy(
            isLocked = true,
            lockEndTime = newEndTime,
            enabled = true
        )

        DbSet.blockTimeRuleDao.update(updatedRule)
        toast("规则已锁定")
    }
}
