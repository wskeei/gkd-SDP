package li.songe.gkd.sdp.a11y

import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.runtime.appDependencies
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.InterceptOverlayService
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import java.util.concurrent.ConcurrentHashMap


object UrlBlockerEngine {
    private const val TAG = "UrlBlockerEngine"

    // 缓存的规则和浏览器配置
    private var cachedRules: List<UrlBlockRule> = emptyList()
    private var cachedBrowsers: Map<String, BrowserConfig> = emptyMap()
    private var cachedGroups: List<UrlRuleGroup> = emptyList()
    private var cachedTimeRules: List<UrlTimeRule> = emptyList()

    // 冷却时间缓存，防止重复触发
    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private val pendingMap = ConcurrentHashMap<String, Boolean>()
    private const val COOLDOWN_MS = 5000L  // 5秒冷却时间

    // 是否启用 URL 拦截
    val enabledFlow = MutableStateFlow(true)

    init {
        // 监听规则和浏览器配置变化
        appScope.launch(appDependencies.dispatchers.io) {
            combine(
                DbSet.urlBlockRuleDao.queryEnabled(),
                DbSet.browserConfigDao.queryEnabled(),
                DbSet.urlRuleGroupDao.queryEnabled(),
                DbSet.urlTimeRuleDao.queryEnabled()
            ) { rules, browsers, groups, timeRules ->
                Quadruple(rules, browsers, groups, timeRules)
            }.collect { (rules, browsers, groups, timeRules) ->
                cachedRules = rules
                cachedBrowsers = browsers.associateBy { it.packageName }
                cachedGroups = groups
                cachedTimeRules = timeRules
                LogUtils.d(
                    "url blocker configuration updated",
                    rules.size + browsers.size + groups.size + timeRules.size,
                )
            }
        }

        // 初始化内置浏览器配置
        appScope.launch(appDependencies.dispatchers.io) {
            DbSet.browserConfigDao.insertIgnore(BrowserConfig.BUILTIN_BROWSERS)
        }
    }

    // 辅助数据类，用于combine四个流
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


    /**
     * 处理无障碍事件，检测浏览器 URL
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        ruleEngine: A11yRuleEngine?,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ) {
        if (!enabledFlow.value) return
        if (cachedRules.isEmpty()) return
        if (ruleEngine == null) return
        if (!isOwnerCurrent(owner)) return

        val packageName = event.packageName?.toString() ?: return
        val browserConfig = cachedBrowsers[packageName] ?: return

        // 只处理内容变化和窗口状态变化事件
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        // 检查冷却时间
        val now = appDependencies.clock.nowEpochMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return
        }

        // 尝试读取 URL
        val url = tryReadUrl(ruleEngine, browserConfig) ?: return

        LogUtils.d("browser URL observed")

        // 检查是否匹配任何规则
        val matchedRule = cachedRules.firstOrNull { it.matches(url) }
        if (matchedRule != null) {
            // 检查时间规则
            if (!shouldBlockRule(matchedRule)) {
                LogUtils.d("URL rule outside active schedule")
                return
            }
            
            if (!isOwnerCurrent(owner)) return
            if (pendingMap.putIfAbsent(packageName, true) != null) return
            LogUtils.d("URL blocker matched a rule", "package=$packageName", "ruleId=${matchedRule.id}")
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "url-blocker", packageName, "matched")
            executeBlock(matchedRule, packageName, owner)
        }
    }

    /**
     * 检查规则是否应该拦截（考虑时间规则）
     */
    private fun shouldBlockRule(rule: UrlBlockRule): Boolean {
        // 0. 如果规则属于组，且组被禁用，则不拦截
        if (rule.groupId > 0) {
            val group = cachedGroups.find { it.id == rule.groupId }
            if (group == null || !group.enabled) {
                return false
            }
        }

        // 1. 检查规则自身的时间规则
        val ruleTimeRules = cachedTimeRules.filter { 
            it.targetType == UrlTimeRule.TARGET_TYPE_RULE && 
            it.targetId == rule.id &&
            it.enabled
        }
        
        // 2. 如果规则属于某个组，检查组的时间规则
        val groupTimeRules = if (rule.groupId > 0) {
            cachedTimeRules.filter { 
                it.targetType == UrlTimeRule.TARGET_TYPE_GROUP && 
                it.targetId == rule.groupId &&
                it.enabled
            }
        } else {
            emptyList()
        }

        val allTimeRules = ruleTimeRules + groupTimeRules

        // 如果没有时间规则，默认全天拦截
        if (allTimeRules.isEmpty()) {
            return true
        }

        // 检查是否有任何一条时间规则当前激活
        return allTimeRules.any { it.isActiveNow() }
    }


    /**
     * 尝试从浏览器读取当前 URL
     */
    private fun tryReadUrl(ruleEngine: A11yRuleEngine, browserConfig: BrowserConfig): String? {
        return try {
            val rootNode = ruleEngine.safeActiveWindow ?: return null
            findUrlBarText(rootNode, browserConfig.urlBarId)
        } catch (error: Exception) {
            LogUtils.d("URL read failed", error)
            null
        }
    }

    /**
     * 在节点树中查找地址栏文本
     */
    private fun findUrlBarText(root: AccessibilityNodeInfo, urlBarId: String): String? {
        // 首先尝试通过 ID 查找
        val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId)
        if (nodes.isNotEmpty()) {
            val text = nodes[0].text?.toString()
            if (!text.isNullOrBlank()) {
                return text
            }
        }

        // 备用方案：遍历查找可能的地址栏
        return findUrlBarTextRecursive(root, 0)
    }

    /**
     * 递归查找地址栏文本（备用方案）
     */
    private fun findUrlBarTextRecursive(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > 10) return null  // 限制深度

        val text = node.text?.toString()
        if (text != null && looksLikeUrl(text)) {
            return text
        }

        for (i in 0 until node.childCount.coerceAtMost(20)) {
            val child = node.getChild(i) ?: continue
            val result = findUrlBarTextRecursive(child, depth + 1)
            if (result != null) return result
        }

        return null
    }

    /**
     * 判断文本是否看起来像 URL
     */
    private fun looksLikeUrl(text: String): Boolean {
        val t = text.trim().lowercase()
        return (t.startsWith("http://") || t.startsWith("https://") || t.contains(".com") ||
                t.contains(".cn") || t.contains(".net") || t.contains(".org")) &&
                !t.contains(" ") && t.length > 5
    }

    /**
     * 执行拦截操作
     */
    private fun executeBlock(
        rule: UrlBlockRule,
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?,
    ) {
        appScope.launch(appDependencies.dispatchers.main) {
            try {
                if (!isOwnerCurrent(owner)) return@launch
                // 1. 先跳转到安全页面
                var redirectAccepted = false
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.redirectUrl)).apply {
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    li.songe.gkd.sdp.app.startActivity(intent)
                    redirectAccepted = true
                    LogUtils.d("URL redirect succeeded", "package=$packageName", "ruleId=${rule.id}")
                } catch (e: Exception) {
                    LogUtils.d("URL redirect rejected", "package=$packageName", "ruleId=${rule.id}", e::class.java.simpleName)
                    // 如果无法在同一浏览器打开，尝试用默认浏览器
                    if (!isOwnerCurrent(owner)) return@launch
                    try {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.redirectUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        li.songe.gkd.sdp.app.startActivity(fallbackIntent)
                        redirectAccepted = true
                    } catch (e2: Exception) {
                        LogUtils.d("URL fallback redirect rejected", "package=$packageName", "ruleId=${rule.id}", e2::class.java.simpleName)
                    }
                }

                // 2. 显示全屏拦截（如果启用）
                val accepted = if (rule.showIntercept) {
                    // 延迟一点显示全屏拦截，让跳转先完成
                    kotlinx.coroutines.delay(300)
                    if (!isOwnerCurrent(owner)) return@launch
                    showInterceptOverlay(rule) == OverlayLaunchResult.Accepted
                } else {
                    redirectAccepted
                }
                if (accepted && isOwnerCurrent(owner)) {
                    cooldownMap[packageName] = appDependencies.clock.nowEpochMillis()
                    sdpRuntimeFeatureCoordinator.recordDecision(owner, "url-blocker", packageName, "overlay_accepted")
                } else if (!accepted) {
                    sdpRuntimeFeatureCoordinator.recordDecision(owner, "url-blocker", packageName, "overlay_rejected")
                }
            } finally {
                pendingMap.remove(packageName)
            }
        }
    }

    private fun isOwnerCurrent(owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?): Boolean {
        return owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner)
    }

    /**
     * 显示全屏拦截界面
     */
    private fun showInterceptOverlay(rule: UrlBlockRule): OverlayLaunchResult {
        val intent = Intent(li.songe.gkd.sdp.app, InterceptOverlayService::class.java).apply {
            putExtra(InterceptOverlayService.EXTRA_SUBS_ID, -2L)  // URL 拦截使用特殊 ID (-2 区别于默认的 -1)
            putExtra(InterceptOverlayService.EXTRA_GROUP_KEY, 0)
            putExtra(InterceptOverlayService.EXTRA_MESSAGE, rule.interceptMessage)
            putExtra(InterceptOverlayService.EXTRA_COOLDOWN, 10)
            putExtra(
                InterceptOverlayService.EXTRA_EVENT_KEY,
                SelfControlElapsedPolicy.urlInterceptEventKey(rule.id),
            )
            putExtra(
                InterceptOverlayService.EXTRA_EVENT_KIND,
                SelfControlAttempt.KIND_URL_INTERCEPT,
            )
            putExtra(InterceptOverlayService.EXTRA_SUBJECT_ID, rule.id.toString())
            putExtra(
                InterceptOverlayService.EXTRA_SUBJECT_LABEL,
                rule.name.ifBlank { "网址规则 #${rule.id}" },
            )
            putExtra(InterceptOverlayService.EXTRA_URL_RULE_ID, rule.id)
            putExtra(InterceptOverlayService.EXTRA_URL_RULE_NAME, rule.name)
        }
        return selfControlOverlayLauncher.launch(intent)
    }

    /**
     * 清除冷却时间缓存
     */
    fun clearCooldown() {
        cooldownMap.clear()
        pendingMap.clear()
        sdpRuntimeFeatureCoordinator.invalidateCurrentApp("url-overlay-mount-failed")
    }
}
