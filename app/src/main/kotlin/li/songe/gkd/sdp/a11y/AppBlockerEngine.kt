package li.songe.gkd.sdp.a11y

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.AppBlockerOverlayService
import li.songe.gkd.sdp.util.LogUtils
import java.util.concurrent.ConcurrentHashMap

object AppBlockerEngine {
    private const val TAG = "AppBlockerEngine"

    // 缓存的规则和应用组
    private var cachedRules: List<BlockTimeRule> = emptyList()
    private var cachedGroups: List<AppGroup> = emptyList()

    // 冷却时间缓存，防止重复触发
    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 2000L  // 2秒冷却时间

    // 是否启用应用拦截引擎
    val enabledFlow = MutableStateFlow(true)

    // 全局锁定状态
    val globalLockFlow = DbSet.appBlockerLockDao.getLock()
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // 所有应用组
    val allGroupsFlow = DbSet.appGroupDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 启用的应用组
    val enabledGroupsFlow = DbSet.appGroupDao.queryEnabled()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 所有规则
    val allRulesFlow = DbSet.blockTimeRuleDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 启用的规则
    val enabledRulesFlow = DbSet.blockTimeRuleDao.queryEnabled()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    init {
        // 监听规则和应用组变化
        appScope.launch(Dispatchers.IO) {
            combine(
                DbSet.blockTimeRuleDao.queryEnabled(),
                DbSet.appGroupDao.queryEnabled()
            ) { rules, groups ->
                rules to groups
            }.collect { (rules, groups) ->
                cachedRules = rules
                cachedGroups = groups
                if (META.debuggable) {
                    Log.d(TAG, "Rules updated: ${rules.size}, Groups: ${groups.size}")
                }
            }
        }
    }

    /**
     * 判断应用是否应该被拦截
     * @return Pair<是否拦截, 拦截消息>
     */
    fun shouldBlock(packageName: String): Pair<Boolean, String?> {
        if (!enabledFlow.value) return false to null

        val effectiveRules = getEffectiveRules(packageName)
        if (effectiveRules.isEmpty()) return false to null

        // 使用最新规则的拦截消息
        return true to effectiveRules.first().interceptMessage
    }

    /**
     * 获取应用的所有生效规则（按创建时间倒序）
     */
    private fun getEffectiveRules(packageName: String): List<BlockTimeRule> {
        // 1. 收集应用的单独规则
        val appRules = cachedRules.filter {
            it.targetType == BlockTimeRule.TARGET_TYPE_APP &&
            it.targetId == packageName &&
            it.isActiveNow()
        }

        // 2. 收集应用所属应用组的规则
        val groupRules = mutableListOf<BlockTimeRule>()
        for (group in cachedGroups) {
            if (group.containsApp(packageName)) {
                val rules = cachedRules.filter {
                    it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                    it.targetId == group.id.toString() &&
                    it.isActiveNow()
                }
                groupRules.addAll(rules)
            }
        }

        // 3. 合并并按创建时间排序（最新的在前）
        return (appRules + groupRules).sortedByDescending { it.createdAt }
    }

    /**
     * 获取应用的所有规则（包括未生效的，用于冲突检测）
     */
    fun getAllRulesForApp(packageName: String): List<BlockTimeRule> {
        // 应用的单独规则
        val appRules = cachedRules.filter {
            it.targetType == BlockTimeRule.TARGET_TYPE_APP &&
            it.targetId == packageName
        }

        // 应用所属应用组的规则
        val groupRules = mutableListOf<BlockTimeRule>()
        for (group in cachedGroups) {
            if (group.containsApp(packageName)) {
                val rules = cachedRules.filter {
                    it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                    it.targetId == group.id.toString()
                }
                groupRules.addAll(rules)
            }
        }

        return (appRules + groupRules).sortedByDescending { it.createdAt }
    }

    /**
     * 检测应用是否有规则冲突
     */
    fun hasConflict(packageName: String): Boolean {
        val rules = getAllRulesForApp(packageName)
        return rules.size > 1
    }

    /**
     * 获取应用所属的所有应用组
     */
    fun getGroupsContaining(packageName: String): List<AppGroup> {
        return cachedGroups.filter { it.containsApp(packageName) }
    }

    /**
     * 处理应用切换事件
     */
    fun onAppChanged(packageName: String, service: A11yService) {
        LogUtils.d("$TAG: onAppChanged called for $packageName, enabled=${enabledFlow.value}")
        
        if (!enabledFlow.value) {
            LogUtils.d("$TAG: App blocker disabled, skipping")
            return
        }

        // 检查冷却时间
        val now = System.currentTimeMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (now - lastTriggerTime < COOLDOWN_MS) {
            LogUtils.d("$TAG: Cooldown active for $packageName")
            return
        }

        // 判断是否应该拦截
        val (shouldBlock, message) = shouldBlock(packageName)
        LogUtils.d("$TAG: shouldBlock=$shouldBlock for $packageName, rules=${cachedRules.size}, groups=${cachedGroups.size}")
        
        if (shouldBlock) {
            cooldownMap[packageName] = now
            LogUtils.d("App blocker blocking: $packageName")
            showBlockerOverlay(service, packageName, message ?: "这真的重要吗？")
        }
    }

    /**
     * 显示应用拦截全屏界面
     */
    private fun showBlockerOverlay(service: A11yService, packageName: String, message: String) {
        try {
            val intent = Intent(service, AppBlockerOverlayService::class.java).apply {
                putExtra("message", message)
                putExtra("blockedApp", packageName)
            }
            service.startService(intent)
        } catch (e: Exception) {
            LogUtils.d("Failed to show app blocker overlay: ${e.message}")
        }
    }

    /**
     * 清除冷却时间缓存
     */
    fun clearCooldown() {
        cooldownMap.clear()
    }
}
