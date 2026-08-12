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
import li.songe.gkd.sdp.util.AutoReenableDisableGuard
import li.songe.gkd.sdp.util.AppBlockerDecisionPolicy
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R

class AppBlockerVm : BaseViewModel() {
    enum class GroupEditorMode {
        Create,
        Edit,
        AppendApps,
    }

    data class GroupPickerConfig(
        val initialSelection: List<String>,
        val excludedApps: Set<String>,
    )

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
    var groupEditorMode by mutableStateOf(GroupEditorMode.Create)

    // 规则表单
    var ruleTargetType by mutableIntStateOf(BlockTimeRule.TARGET_TYPE_APP)
    var ruleTargetId by mutableStateOf("")
    var ruleStartTime by mutableStateOf("22:00")
    var ruleEndTime by mutableStateOf("08:00")
    var ruleDaysOfWeek by mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7))
    var ruleInterceptMessage by mutableStateOf(
        li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message),
    )
    var ruleIsAllowMode by mutableStateOf(false)  // 是否为允许模式（反选）

    // 锁定时长选择
    var selectedLockDuration by mutableIntStateOf(480)  // 默认 8 小时
    var isCustomLockDuration by mutableStateOf(false)
    var customLockDaysText by mutableStateOf("")
    var customLockHoursText by mutableStateOf("")

    fun resetGroupForm() {
        editingGroup = null
        groupEditorMode = GroupEditorMode.Create
        groupName = ""
        groupApps = emptyList()
        showGroupEditor = false
    }

    fun loadGroupForEdit(
        group: AppGroup,
        mode: GroupEditorMode = GroupEditorMode.Edit,
    ) {
        editingGroup = group
        groupEditorMode = mode
        groupName = group.name
        groupApps = group.getAppList()
        showGroupEditor = true
    }

    fun saveGroup() = viewModelScope.launch(Dispatchers.IO) {
        if (groupName.isBlank()) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_7f9cc8a658))
            return@launch
        }
        if (groupApps.isEmpty()) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_53de29c90a))
            return@launch
        }

        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_b60e11702a))
            return@launch
        }
        if (editingGroup?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_2d310a6c50))
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
        toast(if (editingGroup != null) li.songe.gkd.sdp.app.getString(R.string.s_69523749b4) else li.songe.gkd.sdp.app.getString(R.string.s_06802d0346))
        resetGroupForm()
    }

    fun deleteGroup(group: AppGroup) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_f668f3749f))
            return@launch
        }
        if (group.isCurrentlyLocked) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_81287b9dd7))
            return@launch
        }
        DbSet.appGroupDao.delete(group)
        // 同时删除该应用组的所有规则
        DbSet.blockTimeRuleDao.deleteByTarget(BlockTimeRule.TARGET_TYPE_GROUP, group.id.toString())
        toast(li.songe.gkd.sdp.app.getString(R.string.s_dec1fa77b8))
    }

    fun toggleGroupEnabled(group: AppGroup) = viewModelScope.launch(Dispatchers.IO) {
        if (group.enabled && (group.isCurrentlyLocked || globalLockFlow.value?.isCurrentlyLocked == true)) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_19c7b9ed28))
            return@launch
        }
        val requestedEnabled = !group.enabled
        if (shouldConsumeDisableQuota(currentEnabled = group.enabled, requestedEnabled = requestedEnabled)) {
            val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
            if (!attempt.allowed) {
                toast(quotaBlockedToast(attempt.limit))
                return@launch
            }
        }
        DbSet.appGroupDao.update(group.copy(enabled = requestedEnabled))
    }

    fun applyPickedApps(pickedApps: List<String>) {
        groupApps = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = groupApps,
            pickedApps = pickedApps,
            appendOnly = groupEditorMode == GroupEditorMode.AppendApps,
        )
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
        ruleInterceptMessage = li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message)
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
            toast(li.songe.gkd.sdp.app.getString(R.string.s_bb81e3c8bd))
            return@launch
        }
        if (ruleTargetType != BlockTimeRule.TARGET_TYPE_APP &&
            ruleTargetType != BlockTimeRule.TARGET_TYPE_GROUP
        ) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_9d6d4b8017))
            return@launch
        }
        if (!AppBlockerDecisionPolicy.isValidTime(ruleStartTime) ||
            !AppBlockerDecisionPolicy.isValidTime(ruleEndTime)
        ) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_952d0f0784))
            return@launch
        }
        if (ruleDaysOfWeek.isEmpty() || ruleDaysOfWeek.any { it !in 1..7 }) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_97668429f3))
            return@launch
        }

        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_b60e11702a))
            return@launch
        }
        if (editingRule?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_24250499b8))
            return@launch
        }

        // 检查目标对象是否锁定
        if (ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP) {
            val groupId = ruleTargetId.toLongOrNull()
            if (groupId == null || groupId <= 0L) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_179a65fe24))
                return@launch
            }
            val group = DbSet.appGroupDao.getById(groupId)
            if (group == null) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_6be293f190))
                return@launch
            }
            if (group.isCurrentlyLocked) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e8446b01b9))
                return@launch
            }
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
            interceptMessage = ruleInterceptMessage.ifBlank {
                li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message)
            },
            isAllowMode = ruleIsAllowMode
        )

        DbSet.blockTimeRuleDao.insert(rule)
        toast(if (editingRule != null) li.songe.gkd.sdp.app.getString(R.string.s_fccd13d79e) else li.songe.gkd.sdp.app.getString(R.string.s_4a96cba3d5))
        resetRuleForm()
    }

    fun deleteRule(rule: BlockTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_f668f3749f))
            return@launch
        }
        if (rule.isCurrentlyLocked) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_7e2a3403ff))
            return@launch
        }
        DbSet.blockTimeRuleDao.delete(rule)
        toast(li.songe.gkd.sdp.app.getString(R.string.s_91ba569081))
    }

    fun toggleRuleEnabled(rule: BlockTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val requestedEnabled = !rule.enabled
        if (shouldConsumeDisableQuota(currentEnabled = rule.enabled, requestedEnabled = requestedEnabled)) {
            val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
            if (!attempt.allowed) {
                toast(quotaBlockedToast(attempt.limit))
                return@launch
            }
        }
        DbSet.blockTimeRuleDao.update(rule.copy(enabled = requestedEnabled))
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
            toast(li.songe.gkd.sdp.app.getString(R.string.s_40d80a0879))
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
        toast(li.songe.gkd.sdp.app.getString(R.string.s_32850ffc30))
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
            toast(li.songe.gkd.sdp.app.getString(R.string.s_40d80a0879))
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
        toast(li.songe.gkd.sdp.app.getString(R.string.s_cb098574e7))
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
            toast(li.songe.gkd.sdp.app.getString(R.string.s_40d80a0879))
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
        toast(li.songe.gkd.sdp.app.getString(R.string.s_7aa6790ed4))
    }

    companion object {
        fun buildGroupPickerConfig(
            currentApps: List<String>,
            mode: GroupEditorMode,
        ): GroupPickerConfig {
            return if (mode == GroupEditorMode.Create) {
                GroupPickerConfig(
                    initialSelection = currentApps,
                    excludedApps = emptySet(),
                )
            } else {
                GroupPickerConfig(
                    initialSelection = emptyList(),
                    excludedApps = currentApps.toSet(),
                )
            }
        }

        fun shouldConsumeDisableQuota(currentEnabled: Boolean, requestedEnabled: Boolean): Boolean {
            return currentEnabled && !requestedEnabled
        }

        private fun quotaBlockedToast(limit: Int): String {
            return li.songe.gkd.sdp.app.getString(R.string.s_ba1f755996, limit.toString())
        }
    }
}
