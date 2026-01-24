package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.toast

class UrlBlockVm : BaseViewModel() {

    val rulesFlow = DbSet.urlBlockRuleDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val browsersFlow = DbSet.browserConfigDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val enabledRuleCountFlow = DbSet.urlBlockRuleDao.countEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val enabledBrowserCountFlow = DbSet.browserConfigDao.countEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 规则组
    val groupsFlow = DbSet.urlRuleGroupDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 时间规则
    val timeRulesFlow = DbSet.urlTimeRuleDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 锁定状态
    val isLockedFlow = FocusLockUtils.allConstraintsFlow.map { constraints ->
        constraints.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == FocusLockUtils.URL_BLOCKER_SUBS_ID &&
            it.isLocked
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lockEndTimeFlow = FocusLockUtils.allConstraintsFlow.map { constraints ->
        constraints
            .filter {
                it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
                it.subsId == FocusLockUtils.URL_BLOCKER_SUBS_ID &&
                it.isLocked
            }
            .maxOfOrNull { it.lockEndTime } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // 锁定时长选择
    var selectedDuration by mutableIntStateOf(480)  // 默认 8 小时
    var isCustomDuration by mutableStateOf(false)
    var customDaysText by mutableStateOf("")
    var customHoursText by mutableStateOf("")

    // 编辑状态
    var editingRule by mutableStateOf<UrlBlockRule?>(null)
    var editingBrowser by mutableStateOf<BrowserConfig?>(null)
    var editingGroup by mutableStateOf<UrlRuleGroup?>(null)
    var editingTimeRule by mutableStateOf<UrlTimeRule?>(null)

    // 规则表单字段
    var ruleName by mutableStateOf("")
    var rulePattern by mutableStateOf("")
    var ruleMatchType by mutableStateOf(UrlBlockRule.MATCH_TYPE_DOMAIN)
    var ruleRedirectUrl by mutableStateOf(UrlBlockRule.DEFAULT_REDIRECT_URL)
    var ruleShowIntercept by mutableStateOf(true)
    var ruleInterceptMessage by mutableStateOf("这真的重要吗？")
    var ruleGroupId by mutableStateOf(0L)  // 所属规则组

    // 浏览器表单字段
    var browserName by mutableStateOf("")
    var browserPackageName by mutableStateOf("")
    var browserUrlBarId by mutableStateOf("")

    // 规则组表单字段
    var groupName by mutableStateOf("")

    // 时间规则表单字段
    var timeRuleTargetType by mutableStateOf(UrlTimeRule.TARGET_TYPE_RULE)
    var timeRuleTargetId by mutableStateOf("")
    var timeRuleStartTime by mutableStateOf("00:00")
    var timeRuleEndTime by mutableStateOf("23:59")
    var timeRuleDaysOfWeek by mutableStateOf("1,2,3,4,5,6,7")
    var timeRuleInterceptMessage by mutableStateOf("这真的重要吗？")
    var timeRuleIsAllowMode by mutableStateOf(false)

    fun resetRuleForm() {
        editingRule = null
        ruleName = ""
        rulePattern = ""
        ruleMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN
        ruleRedirectUrl = UrlBlockRule.DEFAULT_REDIRECT_URL
        ruleShowIntercept = true
        ruleInterceptMessage = "这真的重要吗？"
        ruleGroupId = 0L
    }

    fun loadRuleForEdit(rule: UrlBlockRule) {
        editingRule = rule
        ruleName = rule.name
        rulePattern = rule.pattern
        ruleMatchType = rule.matchType
        ruleRedirectUrl = rule.redirectUrl
        ruleShowIntercept = rule.showIntercept
        ruleInterceptMessage = rule.interceptMessage
        ruleGroupId = rule.groupId
    }

    fun resetBrowserForm() {
        editingBrowser = null
        browserName = ""
        browserPackageName = ""
        browserUrlBarId = ""
    }

    fun loadBrowserForEdit(browser: BrowserConfig) {
        editingBrowser = browser
        browserName = browser.name
        browserPackageName = browser.packageName
        browserUrlBarId = browser.urlBarId
    }

    fun saveRule() = viewModelScope.launch(Dispatchers.IO) {
        if (rulePattern.isBlank()) {
            toast("请输入网址匹配模式")
            return@launch
        }

        val rule = UrlBlockRule(
            id = editingRule?.id ?: 0,
            pattern = rulePattern.trim(),
            matchType = ruleMatchType,
            enabled = editingRule?.enabled ?: true,
            name = ruleName.ifBlank { rulePattern.trim() },
            redirectUrl = ruleRedirectUrl.ifBlank { UrlBlockRule.DEFAULT_REDIRECT_URL },
            showIntercept = ruleShowIntercept,
            interceptMessage = ruleInterceptMessage.ifBlank { "这真的重要吗？" },
            orderIndex = editingRule?.orderIndex ?: 0,
            groupId = ruleGroupId
        )

        DbSet.urlBlockRuleDao.insert(rule)
        toast(if (editingRule != null) "规则已更新" else "规则已添加")
        resetRuleForm()
    }

    fun deleteRule(rule: UrlBlockRule) = viewModelScope.launch(Dispatchers.IO) {
        if (FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法删除规则")
            return@launch
        }
        DbSet.urlBlockRuleDao.delete(rule)
        toast("规则已删除")
    }

    fun toggleRuleEnabled(rule: UrlBlockRule) = viewModelScope.launch(Dispatchers.IO) {
        // 锁定状态下不允许关闭规则
        if (rule.enabled && FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法关闭规则")
            return@launch
        }
        DbSet.urlBlockRuleDao.update(rule.copy(enabled = !rule.enabled))
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
        if (browser.isBuiltin) {
            toast("内置浏览器不可删除")
            return@launch
        }
        if (FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法删除浏览器")
            return@launch
        }
        DbSet.browserConfigDao.delete(browser)
        toast("浏览器配置已删除")
    }

    fun toggleBrowserEnabled(browser: BrowserConfig) = viewModelScope.launch(Dispatchers.IO) {
        // 锁定状态下不允许关闭浏览器
        if (browser.enabled && FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法关闭浏览器")
            return@launch
        }
        DbSet.browserConfigDao.update(browser.copy(enabled = !browser.enabled))
    }

    fun toggleUrlBlockerEnabled(enabled: Boolean) {
        // 锁定状态下不允许关闭
        if (!enabled && FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法关闭")
            return
        }
        UrlBlockerEngine.enabledFlow.value = enabled
    }

    /**
     * 锁定网址拦截功能
     */
    fun lockUrlBlocker() = viewModelScope.launch(Dispatchers.IO) {
        val durationMinutes = if (isCustomDuration) {
            val days = customDaysText.toIntOrNull() ?: 0
            val hours = customHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val durationMillis = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val currentEndTime = lockEndTimeFlow.value

        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMillis  // 延长锁定
        } else {
            now + durationMillis  // 新锁定
        }

        // 查找现有配置
        val existing = FocusLockUtils.allConstraintsFlow.value.find {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == FocusLockUtils.URL_BLOCKER_SUBS_ID
        }

        val config = ConstraintConfig(
            id = existing?.id ?: 0,
            targetType = ConstraintConfig.TYPE_SUBSCRIPTION,
            subsId = FocusLockUtils.URL_BLOCKER_SUBS_ID,
            appId = null,
            groupKey = null,
            lockEndTime = newEndTime
        )

        DbSet.constraintConfigDao.insert(config)

        // 锁定时自动启用
        UrlBlockerEngine.enabledFlow.value = true

        toast("网址拦截已锁定")
    }

    // ======================== 规则组管理 ========================

    fun resetGroupForm() {
        editingGroup = null
        groupName = ""
    }

    fun loadGroupForEdit(group: UrlRuleGroup) {
        editingGroup = group
        groupName = group.name
    }

    fun saveGroup() = viewModelScope.launch(Dispatchers.IO) {
        if (groupName.isBlank()) {
            toast("请输入规则组名称")
            return@launch
        }

        val group = UrlRuleGroup(
            id = editingGroup?.id ?: 0,
            name = groupName.trim(),
            enabled = editingGroup?.enabled ?: true,
            orderIndex = editingGroup?.orderIndex ?: 0
        )

        DbSet.urlRuleGroupDao.insert(group)
        toast(if (editingGroup != null) "规则组已更新" else "规则组已添加")
        resetGroupForm()
    }

    fun deleteGroup(group: UrlRuleGroup) = viewModelScope.launch(Dispatchers.IO) {
        if (FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法删除规则组")
            return@launch
        }
        // 删除组内所有时间规则
        DbSet.urlTimeRuleDao.deleteByTarget(UrlTimeRule.TARGET_TYPE_GROUP, group.id.toString())
        // 删除规则组
        DbSet.urlRuleGroupDao.delete(group)
        toast("规则组已删除")
    }

    fun toggleGroupEnabled(group: UrlRuleGroup) = viewModelScope.launch(Dispatchers.IO) {
        if (group.enabled && FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法关闭规则组")
            return@launch
        }
        DbSet.urlRuleGroupDao.update(group.copy(enabled = !group.enabled))
    }

    // ======================== 时间规则管理 ========================

    fun resetTimeRuleForm() {
        editingTimeRule = null
        timeRuleTargetType = UrlTimeRule.TARGET_TYPE_RULE
        timeRuleTargetId = ""
        timeRuleStartTime = "00:00"
        timeRuleEndTime = "23:59"
        timeRuleDaysOfWeek = "1,2,3,4,5,6,7"
        timeRuleInterceptMessage = "这真的重要吗？"
        timeRuleIsAllowMode = false
    }

    fun loadTimeRuleForEdit(rule: UrlTimeRule) {
        editingTimeRule = rule
        timeRuleTargetType = rule.targetType
        timeRuleTargetId = rule.targetId
        timeRuleStartTime = rule.startTime
        timeRuleEndTime = rule.endTime
        timeRuleDaysOfWeek = rule.daysOfWeek
        timeRuleInterceptMessage = rule.interceptMessage
        timeRuleIsAllowMode = rule.isAllowMode
    }

    fun applyTimeTemplate(template: UrlTimeRule.Companion.TimeTemplate) {
        timeRuleStartTime = template.startTime
        timeRuleEndTime = template.endTime
        timeRuleDaysOfWeek = template.daysOfWeek
    }

    fun saveTimeRule() = viewModelScope.launch(Dispatchers.IO) {
        if (timeRuleTargetId.isBlank()) {
            toast("请选择拦截对象")
            return@launch
        }

        val rule = UrlTimeRule(
            id = editingTimeRule?.id ?: 0,
            targetType = timeRuleTargetType,
            targetId = timeRuleTargetId,
            startTime = timeRuleStartTime,
            endTime = timeRuleEndTime,
            daysOfWeek = timeRuleDaysOfWeek,
            enabled = editingTimeRule?.enabled ?: true,
            isLocked = editingTimeRule?.isLocked ?: false,
            lockEndTime = editingTimeRule?.lockEndTime ?: 0,
            interceptMessage = timeRuleInterceptMessage.ifBlank { "这真的重要吗？" },
            isAllowMode = timeRuleIsAllowMode
        )

        DbSet.urlTimeRuleDao.insert(rule)
        toast(if (editingTimeRule != null) "时间规则已更新" else "时间规则已添加")
        resetTimeRuleForm()
    }

    fun deleteTimeRule(rule: UrlTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        if (rule.isCurrentlyLocked) {
            toast("该规则已锁定，无法删除")
            return@launch
        }
        if (FocusLockUtils.isUrlBlockerLocked()) {
            toast("网址拦截已锁定，无法删除时间规则")
            return@launch
        }
        DbSet.urlTimeRuleDao.delete(rule)
        toast("时间规则已删除")
    }

    fun toggleTimeRuleEnabled(rule: UrlTimeRule) = viewModelScope.launch(Dispatchers.IO) {
        if (rule.enabled && (rule.isCurrentlyLocked || FocusLockUtils.isUrlBlockerLocked())) {
            toast("规则已锁定，无法关闭")
            return@launch
        }
        DbSet.urlTimeRuleDao.update(rule.copy(enabled = !rule.enabled))
    }

    fun lockTimeRule(rule: UrlTimeRule, durationMinutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val now = System.currentTimeMillis()
        val currentEndTime = if (rule.isCurrentlyLocked) rule.lockEndTime else now
        val newEndTime = currentEndTime + durationMinutes * 60 * 1000L

        DbSet.urlTimeRuleDao.update(rule.copy(
            isLocked = true,
            lockEndTime = newEndTime,
            enabled = true  // 锁定时自动启用
        ))
        toast("时间规则已锁定")
    }
}
