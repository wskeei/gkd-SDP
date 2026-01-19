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
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.toast

class FocusModeVm : BaseViewModel() {

    val allRulesFlow = FocusModeEngine.allRulesFlow
    val activeSessionFlow = FocusModeEngine.activeSessionFlow
    val isActiveFlow = FocusModeEngine.isActiveFlow
    val currentWhitelistFlow = FocusModeEngine.currentWhitelistFlow
    val currentWechatWhitelistFlow = FocusModeEngine.currentWechatWhitelistFlow

    // 微信联系人
    val allWechatContactsFlow = DbSet.wechatContactDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 编辑状态
    var editingRule by mutableStateOf<FocusRule?>(null)
    var showRuleEditor by mutableStateOf(false)

    // 规则表单字段
    var ruleName by mutableStateOf("")
    var ruleType by mutableIntStateOf(FocusRule.RULE_TYPE_QUICK_START)  // 默认快速启动
    var ruleStartTime by mutableStateOf("22:00")
    var ruleEndTime by mutableStateOf("23:00")
    var ruleDaysOfWeek by mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7))
    var ruleDurationHours by mutableIntStateOf(0)
    var ruleDurationMinutes by mutableIntStateOf(30)
    var ruleWhitelistApps by mutableStateOf<List<String>>(emptyList())
    var ruleWechatWhitelist by mutableStateOf<List<String>>(emptyList())
    var ruleInterceptMessage by mutableStateOf("专注当下")
    var ruleIsLocked by mutableStateOf(false)
    var ruleLockDurationMinutes by mutableIntStateOf(30)

    val ruleTotalDurationMinutes: Int
        get() = ruleDurationHours * 60 + ruleDurationMinutes

    // 手动启动表单
    var manualHours by mutableIntStateOf(0)
    var manualMinutes by mutableIntStateOf(30)
    val totalDurationMinutes: Int
        get() = manualHours * 60 + manualMinutes

    var manualWhitelistApps by mutableStateOf<List<String>>(emptyList())
    var manualWechatWhitelist by mutableStateOf<List<String>>(emptyList())
    var manualMessage by mutableStateOf("专注当下")
    var manualIsLocked by mutableStateOf(false)
    var manualLockDurationMinutes by mutableIntStateOf(30)

    // 锁定时长选择
    var selectedLockDuration by mutableIntStateOf(480)  // 默认 8 小时
    var isCustomLockDuration by mutableStateOf(false)
    var customLockDaysText by mutableStateOf("")
    var customLockHoursText by mutableStateOf("")

    // 白名单选择器搜索和过滤
    var whitelistSearchQuery by mutableStateOf("")
    var showSystemAppsInWhitelist by mutableStateOf(false)

    fun resetRuleForm() {
        editingRule = null
        ruleName = ""
        ruleType = FocusRule.RULE_TYPE_QUICK_START
        ruleStartTime = "22:00"
        ruleEndTime = "23:00"
        ruleDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
        ruleDurationHours = 0
        ruleDurationMinutes = 30
        ruleWhitelistApps = emptyList()
        ruleWechatWhitelist = emptyList()
        ruleInterceptMessage = "专注当下"
        ruleIsLocked = false
        ruleLockDurationMinutes = 30
        showRuleEditor = false
    }

    fun loadRuleForEdit(rule: FocusRule) {
        editingRule = rule
        ruleName = rule.name
        ruleType = rule.ruleType
        ruleStartTime = rule.startTime
        ruleEndTime = rule.endTime
        ruleDaysOfWeek = rule.getDaysOfWeekList()
        ruleDurationHours = rule.durationMinutes / 60
        ruleDurationMinutes = rule.durationMinutes % 60
        ruleWhitelistApps = rule.getWhitelistPackages()
        ruleWechatWhitelist = rule.getWechatWhitelist()
        ruleInterceptMessage = rule.interceptMessage
        ruleIsLocked = rule.isLocked
        ruleLockDurationMinutes = rule.lockDurationMinutes
        showRuleEditor = true
    }

    fun saveRule() = viewModelScope.launch(Dispatchers.IO) {
        if (ruleName.isBlank()) {
            toast("请输入规则名称")
            return@launch
        }

        // 快速启动模板验证时长
        if (ruleType == FocusRule.RULE_TYPE_QUICK_START && ruleTotalDurationMinutes < 5) {
            toast("专注时长至少为 5 分钟")
            return@launch
        }

        val rule = FocusRule(
            id = editingRule?.id ?: 0,
            name = ruleName.trim(),
            ruleType = ruleType,
            startTime = ruleStartTime,
            endTime = ruleEndTime,
            durationMinutes = ruleTotalDurationMinutes,
            daysOfWeek = ruleDaysOfWeek.joinToString(","),
            enabled = editingRule?.enabled ?: true,
            whitelistApps = json.encodeToString(ruleWhitelistApps),
            wechatWhitelist = json.encodeToString(ruleWechatWhitelist),
            interceptMessage = ruleInterceptMessage.ifBlank { "专注当下" },
            isLocked = editingRule?.isLocked ?: false,
            lockEndTime = editingRule?.lockEndTime ?: 0,
            lockDurationMinutes = ruleLockDurationMinutes,
            orderIndex = editingRule?.orderIndex ?: 0
        )

        DbSet.focusRuleDao.insert(rule)
        toast(if (editingRule != null) "规则已更新" else "规则已添加")
        resetRuleForm()
    }

    fun deleteRule(rule: FocusRule) = viewModelScope.launch(Dispatchers.IO) {
        if (rule.isCurrentlyLocked) {
            toast("规则已锁定，无法删除")
            return@launch
        }
        DbSet.focusRuleDao.delete(rule)
        toast("规则已删除")
    }

    fun toggleRuleEnabled(rule: FocusRule) = viewModelScope.launch(Dispatchers.IO) {
        // 锁定状态下不允许关闭规则
        if (rule.enabled && rule.isCurrentlyLocked) {
            toast("规则已锁定，无法关闭")
            return@launch
        }
        DbSet.focusRuleDao.update(rule.copy(enabled = !rule.enabled))
    }

    fun startManualSession() = viewModelScope.launch(Dispatchers.IO) {
        if (totalDurationMinutes < 5) {
            toast("专注时长至少为 5 分钟")
            return@launch
        }

        FocusModeEngine.startManualSession(
            durationMinutes = totalDurationMinutes,
            whitelistApps = manualWhitelistApps,
            interceptMessage = manualMessage.ifBlank { "专注当下" },
            isLocked = manualIsLocked,
            lockDurationMinutes = if (manualIsLocked) manualLockDurationMinutes else 0
        )
        toast("专注模式已开始")
    }

    fun stopManualSession() = viewModelScope.launch(Dispatchers.IO) {
        val session = activeSessionFlow.value
        if (session?.isCurrentlyLocked == true) {
            toast("专注模式已锁定，无法提前结束")
            return@launch
        }
        FocusModeEngine.stopManualSession()
        toast("专注模式已结束")
    }

    fun lockRule(rule: FocusRule) = viewModelScope.launch(Dispatchers.IO) {
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
            currentEndTime + durationMillis  // 延长锁定
        } else {
            now + durationMillis  // 新锁定
        }

        val updatedRule = rule.copy(
            isLocked = true,
            lockEndTime = newEndTime,
            enabled = true  // 锁定时自动启用
        )

        DbSet.focusRuleDao.update(updatedRule)
        toast("规则已锁定")
    }

    fun addToRuleWhitelist(packageName: String) {
        if (!ruleWhitelistApps.contains(packageName)) {
            ruleWhitelistApps = ruleWhitelistApps + packageName
        }
    }

    fun removeFromRuleWhitelist(packageName: String) {
        ruleWhitelistApps = ruleWhitelistApps - packageName
    }

    fun addToManualWhitelist(packageName: String) {
        if (!manualWhitelistApps.contains(packageName)) {
            manualWhitelistApps = manualWhitelistApps + packageName
        }
    }

    fun removeFromManualWhitelist(packageName: String) {
        manualWhitelistApps = manualWhitelistApps - packageName
    }

    /**
     * 从当前会话白名单中移除应用
     */
    fun removeFromSessionWhitelist(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
        FocusModeEngine.removeFromWhitelist(packageName)
    }

    /**
     * 从快速启动模板启动专注会话
     */
    fun startQuickRule(rule: FocusRule) = viewModelScope.launch(Dispatchers.IO) {
        if (!rule.isQuickStart) {
            toast("这不是快速启动模板")
            return@launch
        }

        if (rule.durationMinutes < 5) {
            toast("专注时长至少为 5 分钟")
            return@launch
        }

        FocusModeEngine.startManualSession(
            durationMinutes = rule.durationMinutes,
            whitelistApps = rule.getWhitelistPackages(),
            interceptMessage = rule.interceptMessage,
            isLocked = rule.isLocked,
            lockDurationMinutes = rule.lockDurationMinutes
        )
        toast("专注模式已开始")
    }

    // 微信联系人白名单管理
    fun addToRuleWechatWhitelist(wechatId: String) {
        if (!ruleWechatWhitelist.contains(wechatId)) {
            ruleWechatWhitelist = ruleWechatWhitelist + wechatId
        }
    }

    fun removeFromRuleWechatWhitelist(wechatId: String) {
        ruleWechatWhitelist = ruleWechatWhitelist - wechatId
    }

    fun addToManualWechatWhitelist(wechatId: String) {
        if (!manualWechatWhitelist.contains(wechatId)) {
            manualWechatWhitelist = manualWechatWhitelist + wechatId
        }
    }

    fun removeFromManualWechatWhitelist(wechatId: String) {
        manualWechatWhitelist = manualWechatWhitelist - wechatId
    }
}
