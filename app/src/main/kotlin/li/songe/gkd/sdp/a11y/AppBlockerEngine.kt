package li.songe.gkd.sdp.a11y

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.runtime.appDependencies
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.AppBlockerOverlayService
import li.songe.gkd.sdp.util.AppBlockerDecision
import li.songe.gkd.sdp.util.AppBlockerDecisionPolicy
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime

object AppBlockerEngine {
    private const val TAG = "AppBlockerEngine"

    // 缓存的规则和应用组
    @Volatile
    private var cachedSnapshot = AppBlockerDecisionPolicy.Snapshot()
    private val cachedRules: List<BlockTimeRule> get() = cachedSnapshot.rules
    private val cachedGroups: List<AppGroup> get() = cachedSnapshot.groups

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
        appScope.launch(appDependencies.dispatchers.io) {
            combine(
                DbSet.blockTimeRuleDao.queryAll(),
                DbSet.appGroupDao.queryAll()
            ) { rules, groups ->
                rules to groups
            }.collect { (rules, groups) ->
                cachedSnapshot = AppBlockerDecisionPolicy.Snapshot(
                    rules = rules.toList(),
                    groups = groups.toList(),
                )
                LogUtils.d("app blocker configuration updated", rules.size + groups.size)
                sdpRuntimeFeatureCoordinator.reconcileCurrentApp("app-blocker-rules-updated")
            }
        }
    }

    /**
     * 判断应用是否应该被拦截
     * @return Pair<是否拦截, 拦截消息>
     */
    fun shouldBlock(packageName: String): Pair<Boolean, String?> {
        return when (val decision = evaluate(packageName)) {
            is AppBlockerDecision.Block -> true to decision.message
            else -> false to null
        }
    }

    fun evaluate(packageName: String, now: LocalDateTime = LocalDateTime.now()): AppBlockerDecision =
        AppBlockerDecisionPolicy.decide(
            packageName = packageName,
            snapshot = cachedSnapshot,
            now = now,
            enabled = enabledFlow.value,
        )

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
    fun onAppChanged(
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ) {
        LogUtils.d("$TAG: onAppChanged called for $packageName, enabled=${enabledFlow.value}")
        
        if (!enabledFlow.value) {
            LogUtils.d("$TAG: App blocker disabled, skipping")
            return
        }
        if (owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)) return
        if (FocusModeEngine.isActiveFlow.value &&
            !FocusModeEngine.currentWhitelistFlow.value.contains(packageName)
        ) {
            return
        }

        // 检查冷却时间
        val now = appDependencies.clock.elapsedRealtimeMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (cooldownMap.containsKey(packageName) && now - lastTriggerTime < COOLDOWN_MS) {
            LogUtils.d("$TAG: Cooldown active for $packageName")
            return
        }

        // 判断是否应该拦截
        val decision = evaluate(packageName)
        val shouldBlock = decision is AppBlockerDecision.Block
        val blockDecision = decision as? AppBlockerDecision.Block
        val message = blockDecision?.message
        val blockingRule = blockDecision?.ruleSnapshot
        sdpRuntimeFeatureCoordinator.recordDecision(
            owner = owner,
            feature = "app-blocker",
            packageName = packageName,
            decision = decision::class.simpleName ?: "unknown",
        )
        LogUtils.d("$TAG: decision=${decision::class.simpleName} for $packageName, rules=${cachedRules.size}, groups=${cachedGroups.size}")
        
        if (shouldBlock) {
            LogUtils.d("App blocker blocking: $packageName")
            if (owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)) return
            val result = showBlockerOverlay(
                packageName = packageName,
                message = message ?: li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message),
                rule = blockingRule,
                owner = owner,
            )
            sdpRuntimeFeatureCoordinator.recordDecision(
                owner,
                "app-blocker",
                packageName,
                "overlay_${result::class.simpleName ?: "unknown"}",
            )
            if (result == OverlayLaunchResult.Accepted &&
                (owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner))
            ) {
                cooldownMap[packageName] = now
            }
        }
    }

    /**
     * 显示应用拦截全屏界面
     */
    private fun showBlockerOverlay(
        packageName: String,
        message: String,
        rule: BlockTimeRule?,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ): OverlayLaunchResult {
        val intent = android.content.Intent(app, AppBlockerOverlayService::class.java).apply {
            putExtra(AppBlockerOverlayService.EXTRA_MESSAGE, message)
            putExtra(AppBlockerOverlayService.EXTRA_BLOCKED_APP, packageName)
            putExtra(
                AppBlockerOverlayService.EXTRA_EVENT_KEY,
                SelfControlElapsedPolicy.appBlockerEventKey(packageName),
            )
            putExtra(
                AppBlockerOverlayService.EXTRA_EVENT_KIND,
                li.songe.gkd.sdp.data.SelfControlAttempt.KIND_APP_BLOCKER,
            )
            putExtra(AppBlockerOverlayService.EXTRA_SUBJECT_ID, packageName)
            putExtra(AppBlockerOverlayService.EXTRA_SUBJECT_LABEL, packageName)
            rule?.let {
                putExtra(AppBlockerOverlayService.EXTRA_RULE_ID, it.id)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_TARGET_TYPE, it.targetType)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_TARGET_ID, it.targetId)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_START_TIME, it.startTime)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_END_TIME, it.endTime)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_DAYS, it.daysOfWeek)
                putExtra(AppBlockerOverlayService.EXTRA_RULE_ALLOW_MODE, it.isAllowMode)
            }
        }
        return if (owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner)) {
            val result = selfControlOverlayLauncher.launch(intent)
            if (result == OverlayLaunchResult.Accepted &&
                owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)
            ) {
                OverlayLaunchResult.RuntimeUnavailable
            } else {
                result
            }
        } else {
            OverlayLaunchResult.RuntimeUnavailable
        }
    }

    /**
     * 清除冷却时间缓存
     */
    fun clearCooldown() {
        cooldownMap.clear()
        sdpRuntimeFeatureCoordinator.invalidateCurrentApp("app-blocker-overlay-mount-failed")
    }

    fun getRuleById(ruleId: Long): BlockTimeRule? = cachedRules.firstOrNull { it.id == ruleId }
}
