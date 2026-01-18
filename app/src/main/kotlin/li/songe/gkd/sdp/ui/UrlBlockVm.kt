package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.UrlBlockRule
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

    // 规则表单字段
    var ruleName by mutableStateOf("")
    var rulePattern by mutableStateOf("")
    var ruleMatchType by mutableStateOf(UrlBlockRule.MATCH_TYPE_DOMAIN)
    var ruleRedirectUrl by mutableStateOf(UrlBlockRule.DEFAULT_REDIRECT_URL)
    var ruleShowIntercept by mutableStateOf(true)
    var ruleInterceptMessage by mutableStateOf("这真的重要吗？")

    // 浏览器表单字段
    var browserName by mutableStateOf("")
    var browserPackageName by mutableStateOf("")
    var browserUrlBarId by mutableStateOf("")

    fun resetRuleForm() {
        editingRule = null
        ruleName = ""
        rulePattern = ""
        ruleMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN
        ruleRedirectUrl = UrlBlockRule.DEFAULT_REDIRECT_URL
        ruleShowIntercept = true
        ruleInterceptMessage = "这真的重要吗？"
    }

    fun loadRuleForEdit(rule: UrlBlockRule) {
        editingRule = rule
        ruleName = rule.name
        rulePattern = rule.pattern
        ruleMatchType = rule.matchType
        ruleRedirectUrl = rule.redirectUrl
        ruleShowIntercept = rule.showIntercept
        ruleInterceptMessage = rule.interceptMessage
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
            orderIndex = editingRule?.orderIndex ?: 0
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
}
