package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.toast

class UrlBlockVm : BaseViewModel() {

    // ======================== Flows ========================

    // 所有的规则组
    val allGroupsFlow = DbSet.urlRuleGroupDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 所有的时间规则
    val allTimeRulesFlow = DbSet.urlTimeRuleDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 所有的网址规则
    val allUrlRulesFlow = DbSet.urlBlockRuleDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 全局锁定状态
    val globalLockFlow = DbSet.urlBlockerLockDao.getLock()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 浏览器配置（原有功能保留）
    val browsersFlow = DbSet.browserConfigDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ======================== UI States ========================

    // 编辑器显示状态
    var showGroupEditor by mutableStateOf(false)
    var showUrlEditor by mutableStateOf(false)
    var showTimeRuleEditor by mutableStateOf(false)
    var showBrowserEditor by mutableStateOf(false)
    var showBrowserList by mutableStateOf(false)

    // 当前编辑的对象
    var editingGroup by mutableStateOf<UrlRuleGroup?>(null)
    var editingUrlRule by mutableStateOf<UrlBlockRule?>(null)
    var editingTimeRule by mutableStateOf<UrlTimeRule?>(null)
    var editingBrowser by mutableStateOf<BrowserConfig?>(null)

    // 锁定时长选择
    var selectedLockDuration by mutableIntStateOf(480)  // 默认 8 小时
    var isCustomLockDuration by mutableStateOf(false)
    var customLockDaysText by mutableStateOf("")
    var customLockHoursText by mutableStateOf("")

    // --- 规则组表单 ---
    var groupName by mutableStateOf("")
    var groupQuickUrls by mutableStateOf("")

    // --- 网址规则表单 ---
    var urlPattern by mutableStateOf("")
    var urlMatchType by mutableIntStateOf(UrlBlockRule.MATCH_TYPE_DOMAIN)
    var urlName by mutableStateOf("")
    var urlRedirectUrl by mutableStateOf(UrlBlockRule.DEFAULT_REDIRECT_URL)
    var urlShowIntercept by mutableStateOf(true)
    var urlInterceptMessage by mutableStateOf("这真的重要吗？")
    var urlGroupId by mutableStateOf(0L)  // 0 表示未分组

    // --- 时间规则表单 ---
    var timeRuleTargetType by mutableIntStateOf(UrlTimeRule.TARGET_TYPE_RULE)
    var timeRuleTargetId by mutableStateOf(0L)
    var timeRuleStartTime by mutableStateOf("22:00")
    var timeRuleEndTime by mutableStateOf("08:00")
    var timeRuleDaysOfWeek by mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7))
    var timeRuleInterceptMsg by mutableStateOf("这真的重要吗？")
    var timeRuleIsAllowMode by mutableStateOf(false)

    // --- 浏览器表单 ---
    var browserName by mutableStateOf("")
    var browserPackageName by mutableStateOf("")
    var browserUrlBarId by mutableStateOf("")

    // ======================== 规则组 Logic ========================

    fun resetGroupForm() {
        editingGroup = null
        groupName = ""
        groupQuickUrls = ""
        showGroupEditor = false
    }

    fun loadGroupForEdit(group: UrlRuleGroup) {
        editingGroup = group
        groupName = group.name
        showGroupEditor = true
    }

    fun saveGroup() = viewModelScope.launch(Dispatchers.IO) {
        if (groupName.isBlank()) {
            toast("请输入规则组名称")
            return@launch
        }

        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法修改")
            return@launch
        }
        if (editingGroup?.isCurrentlyLocked == true) {
            toast("该组已锁定，无法修改")
            return@launch
        }

        val group = UrlRuleGroup(
            id = editingGroup?.id ?: 0,
            name = groupName.trim(),
            enabled = editingGroup?.enabled ?: true,
            isLocked = editingGroup?.isLocked ?: false,
            lockEndTime = editingGroup?.lockEndTime ?: 0,
            orderIndex = editingGroup?.orderIndex ?: 0
        )

        val groupId = DbSet.urlRuleGroupDao.insert(group)

        // 批量添加网址
        val urls = groupQuickUrls.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        urls.forEach { url ->
            val rule = UrlBlockRule(
                pattern = url,
                name = url,
                groupId = groupId
            )
            DbSet.urlBlockRuleDao.insert(rule)
        }

        toast(if (editingGroup != null) "规则组已更新" else "规则组已添加")
        resetGroupForm()
    }

    fun deleteGroup(group: UrlRuleGroup) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法删除")
            return@launch
        }
        if (group.isCurrentlyLocked) {
            toast("规则组已锁定，无法删除")
            return@launch
        }
        
        // 级联删除：删除该组下的所有时间规则
        DbSet.urlTimeRuleDao.deleteByTarget(UrlTimeRule.TARGET_TYPE_GROUP, group.id)
        
        // 级联删除：删除该组下的所有网址规则
        val rulesInGroup = DbSet.urlBlockRuleDao.queryByGroupId(group.id).firstOrNull() ?: emptyList()
        rulesInGroup.forEach { rule ->
            DbSet.urlBlockRuleDao.delete(rule)
        }

        DbSet.urlRuleGroupDao.delete(group)
        toast("规则组已删除")
    }

    fun toggleGroupEnabled(group: UrlRuleGroup) = viewModelScope.launch(Dispatchers.IO) {
        if (group.enabled && (group.isCurrentlyLocked || globalLockFlow.value?.isCurrentlyLocked == true)) {
             // 锁定时允许开启，但不允许关闭 (AppBlocker 逻辑：锁定时无法修改)
             // 实际上 AppBlocker 逻辑是：isLocked 则无法 toggle。
             if (group.isCurrentlyLocked) {
                 toast("规则组已锁定，无法关闭")
                 return@launch
             }
        }
        DbSet.urlRuleGroupDao.update(group.copy(enabled = !group.enabled))
    }

    // ======================== URL 规则 Logic ========================

    fun resetUrlForm() {
        editingUrlRule = null
        editingTimeRule = null
        urlPattern = ""
        urlMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN
        urlName = ""
        urlRedirectUrl = UrlBlockRule.DEFAULT_REDIRECT_URL
        urlShowIntercept = true
        urlInterceptMessage = "这真的重要吗？"
        urlGroupId = 0L
        
        // 重置时间规则字段为默认值 (全天拦截)
        timeRuleStartTime = "00:00"
        timeRuleEndTime = "23:59"
        timeRuleDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
        timeRuleIsAllowMode = false
        
        showUrlEditor = false
    }

    fun loadUrlForEdit(rule: UrlBlockRule) = viewModelScope.launch {
        editingUrlRule = rule
        urlPattern = rule.pattern
        urlMatchType = rule.matchType
        urlName = rule.name
        urlRedirectUrl = rule.redirectUrl
        urlShowIntercept = rule.showIntercept
        urlInterceptMessage = rule.interceptMessage
        urlGroupId = rule.groupId
        
        // 尝试加载该规则下的第一个时间规则
        val timeRules = DbSet.urlTimeRuleDao.queryByTarget(UrlTimeRule.TARGET_TYPE_RULE, rule.id).firstOrNull() ?: emptyList()
        if (timeRules.isNotEmpty()) {
            val tr = timeRules.first()
            editingTimeRule = tr
            timeRuleStartTime = tr.startTime
            timeRuleEndTime = tr.endTime
            timeRuleDaysOfWeek = tr.getDaysOfWeekList()
            timeRuleInterceptMsg = tr.interceptMessage
            timeRuleIsAllowMode = tr.isAllowMode
        } else {
            // 如果没有时间规则，重置为默认值
            editingTimeRule = null
            timeRuleStartTime = "00:00"
            timeRuleEndTime = "23:59"
            timeRuleDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
            timeRuleIsAllowMode = false
        }
        
        showUrlEditor = true
    }

    fun saveUrlRule() = viewModelScope.launch(Dispatchers.IO) {
        if (urlPattern.isBlank()) {
            toast("请输入网址匹配模式")
            return@launch
        }

        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法修改")
            return@launch
        }
        if (editingUrlRule?.isCurrentlyLocked == true) {
            toast("该规则已锁定，无法修改")
            return@launch
        }
        if (urlGroupId > 0) {
            val group = DbSet.urlRuleGroupDao.getById(urlGroupId)
            if (group?.isCurrentlyLocked == true) {
                toast("所属规则组已锁定，无法修改组内规则")
                return@launch
            }
        }

        val rule = UrlBlockRule(
            id = editingUrlRule?.id ?: 0,
            pattern = urlPattern.trim(),
            matchType = urlMatchType,
            enabled = editingUrlRule?.enabled ?: true,
            name = urlName.ifBlank { urlPattern.trim() },
            redirectUrl = urlRedirectUrl.ifBlank { UrlBlockRule.DEFAULT_REDIRECT_URL },
            showIntercept = urlShowIntercept,
            interceptMessage = urlInterceptMessage.ifBlank { "这真的重要吗？" },
            orderIndex = editingUrlRule?.orderIndex ?: 0,
            groupId = urlGroupId,
            isLocked = editingUrlRule?.isLocked ?: false,
            lockEndTime = editingUrlRule?.lockEndTime ?: 0
        )

        val ruleId = DbSet.urlBlockRuleDao.insert(rule)

        // 同时保存/更新关联的时间规则
        val tr = UrlTimeRule(
            id = if (editingUrlRule != null) (editingTimeRule?.id ?: 0) else 0,
            targetType = UrlTimeRule.TARGET_TYPE_RULE,
            targetId = ruleId,
            startTime = timeRuleStartTime,
            endTime = timeRuleEndTime,
            daysOfWeek = timeRuleDaysOfWeek.joinToString(","),
            enabled = true,
            isAllowMode = timeRuleIsAllowMode,
            interceptMessage = urlInterceptMessage.ifBlank { "这真的重要吗？" }
        )
        DbSet.urlTimeRuleDao.insert(tr)

        toast(if (editingUrlRule != null) "规则已更新" else "规则已添加")
        resetUrlForm()
    }

    fun deleteUrlRule(rule: UrlBlockRule) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法删除")
            return@launch
        }
        if (rule.isCurrentlyLocked) {
            toast("规则已锁定，无法删除")
            return@launch
        }
        // 删除关联的时间规则
        DbSet.urlTimeRuleDao.deleteByTarget(UrlTimeRule.TARGET_TYPE_RULE, rule.id)
        
        DbSet.urlBlockRuleDao.delete(rule)
        toast("规则已删除")
    }

    fun toggleUrlRuleEnabled(rule: UrlBlockRule) = viewModelScope.launch(Dispatchers.IO) {
        // 锁定受 Global Lock 和 自身 Lock 控制
        val globalLock = globalLockFlow.value
        if (rule.enabled && (globalLock?.isCurrentlyLocked == true || rule.isCurrentlyLocked)) {
            toast("规则已锁定，无法关闭")
            return@launch
        }
        DbSet.urlBlockRuleDao.update(rule.copy(enabled = !rule.enabled))
    }

    // ======================== 时间规则 Logic ========================

    fun resetTimeRuleForm() {
        editingTimeRule = null
        timeRuleTargetType = UrlTimeRule.TARGET_TYPE_RULE
        timeRuleTargetId = 0L
        timeRuleStartTime = "22:00"
        timeRuleEndTime = "08:00"
        timeRuleDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7)
        timeRuleInterceptMsg = "这真的重要吗？"
        timeRuleIsAllowMode = false
        showTimeRuleEditor = false
    }

    fun loadTimeRuleForEdit(rule: UrlTimeRule) {
        editingTimeRule = rule
        timeRuleTargetType = rule.targetType
        timeRuleTargetId = rule.targetId
        timeRuleStartTime = rule.startTime
        timeRuleEndTime = rule.endTime
        timeRuleDaysOfWeek = rule.getDaysOfWeekList()
        timeRuleInterceptMsg = rule.interceptMessage
        timeRuleIsAllowMode = rule.isAllowMode
        showTimeRuleEditor = true
    }

    fun applyTimeTemplate(template: UrlTimeRule.Companion.TimeTemplate) {
        timeRuleStartTime = template.startTime
        timeRuleEndTime = template.endTime
        timeRuleDaysOfWeek = template.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun saveTimeRule() = viewModelScope.launch(Dispatchers.IO) {
        if (timeRuleTargetId == 0L) {
            toast("请选择拦截对象")
            return@launch
        }

        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法修改")
            return@launch
        }
        if (editingTimeRule?.isCurrentlyLocked == true) {
            toast("该时间规则已锁定，无法修改")
            return@launch
        }
        
        // 检查目标对象（规则或组）是否锁定
        if (timeRuleTargetType == UrlTimeRule.TARGET_TYPE_RULE) {
            val rule = DbSet.urlBlockRuleDao.getById(timeRuleTargetId)
            if (rule?.isCurrentlyLocked == true) {
                toast("目标规则已锁定，无法修改其时间规则")
                return@launch
            }
        } else {
            val group = DbSet.urlRuleGroupDao.getById(timeRuleTargetId)
            if (group?.isCurrentlyLocked == true) {
                toast("目标规则组已锁定，无法修改其时间规则")
                return@launch
            }
        }

        val rule = UrlTimeRule(
            id = editingTimeRule?.id ?: 0,
            targetType = timeRuleTargetType,
            targetId = timeRuleTargetId,
            startTime = timeRuleStartTime,
            endTime = timeRuleEndTime,
            daysOfWeek = timeRuleDaysOfWeek.joinToString(","),
            enabled = editingTimeRule?.enabled ?: true,
            isLocked = editingTimeRule?.isLocked ?: false,
            lockEndTime = editingTimeRule?.lockEndTime ?: 0,
            createdAt = editingTimeRule?.createdAt ?: System.currentTimeMillis(),
            interceptMessage = timeRuleInterceptMsg.ifBlank { "这真的重要吗？" },
            isAllowMode = timeRuleIsAllowMode
        )

        DbSet.urlTimeRuleDao.insert(rule)
        toast(if (editingTimeRule != null) "时间规则已更新" else "时间规则已添加")
        resetTimeRuleForm()
    }

    fun deleteTimeRule(rule: UrlTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法删除")
            return@launch
        }
        if (rule.isCurrentlyLocked) {
            toast("该规则已锁定，无法删除")
            return@launch
        }
        DbSet.urlTimeRuleDao.delete(rule)
        toast("时间规则已删除")
    }

    fun toggleTimeRuleEnabled(rule: UrlTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        if (rule.enabled && (rule.isCurrentlyLocked || globalLockFlow.value?.isCurrentlyLocked == true)) {
            toast("规则已锁定，无法关闭")
            return@launch
        }
        DbSet.urlTimeRuleDao.update(rule.copy(enabled = !rule.enabled))
    }

    // ======================== 锁定 Logic ========================

    private fun calculateLockEndTime(durationMinutes: Int = -1): Long {
         val minutes = if (durationMinutes > 0) durationMinutes else {
             if (isCustomLockDuration) {
                val days = customLockDaysText.toIntOrNull() ?: 0
                val hours = customLockHoursText.toIntOrNull() ?: 0
                days * 24 * 60 + hours * 60
            } else {
                selectedLockDuration
            }
         }
         
         if (minutes <= 0) return 0L
         return minutes * 60 * 1000L
    }

    fun lockGlobal() = viewModelScope.launch(Dispatchers.IO) {
        val durationMillis = calculateLockEndTime()
        if (durationMillis == 0L) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val now = System.currentTimeMillis()
        val currentLock = globalLockFlow.value
        val currentEndTime = currentLock?.lockEndTime ?: now

        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMillis
        } else {
            now + durationMillis
        }

        val lock = UrlBlockerLock(
            id = 1,
            isLocked = true,
            lockEndTime = newEndTime
        )

        DbSet.urlBlockerLockDao.insert(lock)
        toast("全局锁定已设置")
    }

    fun lockGroup(group: UrlRuleGroup) = viewModelScope.launch(Dispatchers.IO) {
        val durationMillis = calculateLockEndTime()
        if (durationMillis == 0L) {
            toast("请输入有效的锁定时长")
            return@launch
        }

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

        DbSet.urlRuleGroupDao.update(updatedGroup)
        toast("规则组已锁定")
    }

    fun lockUrlRule(rule: UrlBlockRule) = viewModelScope.launch(Dispatchers.IO) {
        val durationMillis = calculateLockEndTime()
        if (durationMillis == 0L) {
            toast("请输入有效的锁定时长")
            return@launch
        }

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
            enabled = true  // 锁定时自动启用
        )

        DbSet.urlBlockRuleDao.update(updatedRule)
        toast("规则已锁定")
    }

    fun lockTimeRule(rule: UrlTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        val durationMillis = calculateLockEndTime()
        if (durationMillis == 0L) {
             toast("请输入有效的锁定时长")
            return@launch
        }

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

        DbSet.urlTimeRuleDao.update(updatedRule)
        toast("时间规则已锁定")
    }

    // ======================== 浏览器 Logic (保留) ========================
    
    fun resetBrowserForm() {
        editingBrowser = null
        browserName = ""
        browserPackageName = ""
        browserUrlBarId = ""
        showBrowserEditor = false
    }

    fun loadBrowserForEdit(browser: BrowserConfig) {
        editingBrowser = browser
        browserName = browser.name
        browserPackageName = browser.packageName
        browserUrlBarId = browser.urlBarId
        showBrowserEditor = true
    }

    fun saveBrowser() = viewModelScope.launch(Dispatchers.IO) {
        if (browserPackageName.isBlank() || browserUrlBarId.isBlank()) {
            toast("请填写完整的浏览器信息")
            return@launch
        }

        val browser = BrowserConfig(
            packageName = browserPackageName.trim(),
            name = browserName.ifBlank { browserPackageName.trim() },
            urlBarId = browserUrlBarId.trim(),
            enabled = editingBrowser?.enabled ?: true,
            isBuiltin = editingBrowser?.isBuiltin ?: false
        )

        DbSet.browserConfigDao.insert(browser)
        toast(if (editingBrowser != null) "浏览器配置已更新" else "浏览器已添加")
        resetBrowserForm()
    }

    fun deleteBrowser(browser: BrowserConfig) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (globalLock?.isCurrentlyLocked == true) {
             toast("全局锁定中，无法删除浏览器")
             return@launch
        }
        if (browser.isBuiltin) {
            toast("内置浏览器不可删除")
            return@launch
        }
        DbSet.browserConfigDao.delete(browser)
        toast("浏览器配置已删除")
    }
    
    fun toggleBrowserEnabled(browser: BrowserConfig) = viewModelScope.launch(Dispatchers.IO) {
        val globalLock = globalLockFlow.value
        if (browser.enabled && globalLock?.isCurrentlyLocked == true) {
            toast("全局锁定中，无法关闭")
            return@launch
        }
        DbSet.browserConfigDao.update(browser.copy(enabled = !browser.enabled))
    }
}