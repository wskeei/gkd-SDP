package li.songe.gkd.sdp.a11y

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.InterceptOverlayService
import li.songe.gkd.sdp.util.LogUtils
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
    private const val COOLDOWN_MS = 5000L  // 5秒冷却时间

    // 是否启用 URL 拦截
    val enabledFlow = MutableStateFlow(true)

    init {
        // 监听规则和浏览器配置变化
        appScope.launch(Dispatchers.IO) {
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
                if (META.debuggable) {
                    Log.d(TAG, "Rules updated: ${rules.size}, Browsers: ${browsers.size}, Groups: ${groups.size}, TimeRules: ${timeRules.size}")
                }
            }
        }

        // 初始化内置浏览器配置
        appScope.launch(Dispatchers.IO) {
            DbSet.browserConfigDao.insertIgnore(BrowserConfig.BUILTIN_BROWSERS)
        }
    }

    // 辅助数据类，用于combine四个流
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


    /**
     * 处理无障碍事件，检测浏览器 URL
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, service: A11yService) {
        if (!enabledFlow.value) return
        if (cachedRules.isEmpty()) return

        val packageName = event.packageName?.toString() ?: return
        val browserConfig = cachedBrowsers[packageName] ?: return

        // 只处理内容变化和窗口状态变化事件
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        // 检查冷却时间
        val now = System.currentTimeMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return
        }

        // 尝试读取 URL
        val url = tryReadUrl(service, browserConfig) ?: return

        if (META.debuggable) {
            Log.d(TAG, "Detected URL: $url in $packageName")
        }

        // 检查是否匹配任何规则
        val matchedRule = cachedRules.firstOrNull { it.matches(url) }
        if (matchedRule != null) {
            // 检查时间规则
            if (!shouldBlockRule(matchedRule)) {
                if (META.debuggable) {
                    Log.d(TAG, "Rule ${matchedRule.name} matched but not active now due to time rules")
                }
                return
            }
            
            cooldownMap[packageName] = now
            LogUtils.d("URL Blocked: $url matched rule: ${matchedRule.name}")
            executeBlock(service, matchedRule, packageName)
        }
    }

    /**
     * 检查规则是否应该拦截（考虑时间规则）
     */
    private fun shouldBlockRule(rule: UrlBlockRule): Boolean {
        // 1. 检查规则自身的时间规则
        val ruleTimeRules = cachedTimeRules.filter { 
            it.targetType == UrlTimeRule.TARGET_TYPE_RULE && 
            it.targetId == rule.id.toString() &&
            it.enabled
        }
        
        // 2. 如果规则属于某个组，检查组的时间规则
        val groupTimeRules = if (rule.groupId > 0) {
            val group = cachedGroups.find { it.id == rule.groupId }
            if (group != null && group.enabled) {
                cachedTimeRules.filter { 
                    it.targetType == UrlTimeRule.TARGET_TYPE_GROUP && 
                    it.targetId == rule.groupId.toString() &&
                    it.enabled
                }
            } else {
                emptyList()
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
    private fun tryReadUrl(service: A11yService, browserConfig: BrowserConfig): String? {
        return try {
            val rootNode = service.safeActiveWindow ?: return null
            findUrlBarText(rootNode, browserConfig.urlBarId)
        } catch (e: Exception) {
            if (META.debuggable) {
                Log.e(TAG, "Failed to read URL", e)
            }
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
    private fun executeBlock(service: A11yService, rule: UrlBlockRule, packageName: String) {
        appScope.launch(Dispatchers.Main) {
            // 1. 先跳转到安全页面
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.redirectUrl)).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
                LogUtils.d("Redirected to: ${rule.redirectUrl}")
            } catch (e: Exception) {
                LogUtils.d("Failed to redirect: ${e.message}")
                // 如果无法在同一浏览器打开，尝试用默认浏览器
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.redirectUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    LogUtils.d("Fallback redirect also failed: ${e2.message}")
                }
            }

            // 2. 显示全屏拦截（如果启用）
            if (rule.showIntercept) {
                // 延迟一点显示全屏拦截，让跳转先完成
                kotlinx.coroutines.delay(300)
                showInterceptOverlay(service, rule)
            }
        }
    }

    /**
     * 显示全屏拦截界面
     */
    private fun showInterceptOverlay(service: A11yService, rule: UrlBlockRule) {
        try {
            val intent = Intent(service, InterceptOverlayService::class.java).apply {
                putExtra("subsId", -2L)  // URL 拦截使用特殊 ID (-2 区别于默认的 -1)
                putExtra("groupKey", rule.id.toInt())
                putExtra("message", rule.interceptMessage)
                putExtra("cooldown", 10)
            }
            service.startService(intent)
        } catch (e: Exception) {
            LogUtils.d("Failed to show intercept overlay: ${e.message}")
        }
    }

    /**
     * 清除冷却时间缓存
     */
    fun clearCooldown() {
        cooldownMap.clear()
    }
}
