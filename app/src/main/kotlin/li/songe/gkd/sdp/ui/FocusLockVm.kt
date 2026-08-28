package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.FocusLock
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.data.ResolvedAppGroup
import li.songe.gkd.sdp.data.ResolvedGroup
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.AutoReenableDisableGuard
import li.songe.gkd.sdp.util.AutoReenablePolicy
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.toast
import kotlinx.coroutines.flow.update

data class RuleState(
    val group: ResolvedGroup,
    val interceptConfig: InterceptConfig?, // Changed from Boolean to Config object
    val isSelectedForLock: Boolean,
    val isLocked: Boolean,
    val lockEndTime: Long,
    val lockedBy: Int // 0=None, 1=Self, 2=Parent(App), 3=Parent(Subs)
)

data class AppState(
    val appId: String,
    val appName: String,
    val rules: List<RuleState>,
    val isLocked: Boolean,
    val lockEndTime: Long,
    val allInterceptEnabled: Boolean
)

data class SubscriptionState(
    val subsId: Long,
    val subsName: String,
    val apps: List<AppState>,
    val globalRules: List<RuleState>,
    val isLocked: Boolean,
    val lockEndTime: Long,
    val allInterceptEnabled: Boolean
)

data class AutoReenableIntervalUpdateResult(
    val accepted: Boolean,
    val intervalMinutes: Int,
    val changedAt: Long,
    val remainingCooldownMs: Long,
)

data class AutoReenableUiState(
    val canEditInterval: Boolean,
    val nextEditableAt: Long,
    val nextEnforceAt: Long,
    val dailyDisableLimit: Int,
    val dailyDisableUsed: Int,
    val dailyDisableRemaining: Int,
    val nextDailyResetAt: Long,
)

class FocusLockVm : BaseViewModel() {
    private val selectedRulesSetFlow = MutableStateFlow<Set<FocusLock.LockedRule>>(emptySet())
    var selectedDuration by mutableIntStateOf(480)
    var isCustomDuration by mutableStateOf(false)
    var customDaysText by mutableStateOf("")
    var customHoursText by mutableStateOf("")

    // Map expanded states for Subscriptions and Apps
    val expandedSubs = MutableStateFlow<Set<Long>>(emptySet())
    val expandedApps = MutableStateFlow<Set<String>>(emptySet()) // "subsId_appId"

    val subStatesFlow: StateFlow<List<SubscriptionState>> = combine(
        ruleSummaryFlow,
        DbSet.interceptConfigDao.queryAll(),
        selectedRulesSetFlow,
        FocusLockUtils.allConstraintsFlow
    ) { summary, interceptConfigs, selectedRules, constraints ->
        val now = System.currentTimeMillis()
        val latestConfigByKey = latestInterceptConfigByKey(interceptConfigs)

        // Helper to check constraints
        fun getLockStatus(targetType: Int, subsId: Long, appId: String?, groupKey: Int?): Pair<Boolean, Long> {
            val constraint = constraints.find { 
                it.targetType == targetType && 
                it.subsId == subsId && 
                it.appId == appId && 
                it.groupKey == groupKey 
            }
            return if (constraint != null && constraint.lockEndTime > now) {
                true to constraint.lockEndTime
            } else {
                false to 0L
            }
        }

        // Group by Subscription
        val subsGroups = summary.appIdToAllGroups.values.flatten().groupBy { it.subscription }
        val globalGroups = summary.globalGroups.groupBy { it.subscription }
        val allSubs = (subsGroups.keys + globalGroups.keys).distinctBy { it.id }

        allSubs.map { subs ->
            val (subsLocked, subsEndTime) = getLockStatus(ConstraintConfig.TYPE_SUBSCRIPTION, subs.id, null, null)

            // Process Apps under this Subs
            val appGroups = subsGroups[subs] ?: emptyList()
            val apps = appGroups.groupBy { (it as ResolvedAppGroup).app }.map { (app, groups) ->
                val (appLocked, appEndTime) = getLockStatus(ConstraintConfig.TYPE_APP, subs.id, app.id, null)
                val effectiveAppLocked = subsLocked || appLocked
                val effectiveAppEndTime = maxOf(subsEndTime, appEndTime)

                val ruleStates = groups.map { group ->
                    val (ruleLocked, ruleEndTime) = getLockStatus(ConstraintConfig.TYPE_RULE_GROUP, subs.id, app.id, group.group.key)
                    val effectiveRuleLocked = effectiveAppLocked || ruleLocked
                    val effectiveRuleEndTime = maxOf(effectiveAppEndTime, ruleEndTime)
                    val lockedBy = when {
                        ruleLocked -> 1
                        appLocked -> 2
                        subsLocked -> 3
                        else -> 0
                    }
                    val ruleKey = FocusLock.LockedRule(subs.id, group.group.key, app.id)
                    val config = latestConfigByKey[Triple(subs.id, app.id, group.group.key)]
                    
                    RuleState(
                        group = group,
                        interceptConfig = config,
                        isSelectedForLock = selectedRules.contains(ruleKey),
                        isLocked = effectiveRuleLocked,
                        lockEndTime = effectiveRuleEndTime,
                        lockedBy = lockedBy
                    )
                }
                val allEnabled = ruleStates.isNotEmpty() && ruleStates.all { it.interceptConfig?.enabled == true }
                AppState(
                    appId = app.id,
                    appName = app.name ?: app.id,
                    rules = ruleStates,
                    isLocked = effectiveAppLocked,
                    lockEndTime = effectiveAppEndTime,
                    allInterceptEnabled = allEnabled
                )
            }.filter { it.rules.isNotEmpty() }

            // Process Global Rules
            val gGroups = globalGroups[subs] ?: emptyList()
            val gRuleStates = gGroups.map { group ->
                val (ruleLocked, ruleEndTime) = getLockStatus(ConstraintConfig.TYPE_RULE_GROUP, subs.id, null, group.group.key)
                val effectiveRuleLocked = subsLocked || ruleLocked
                val effectiveRuleEndTime = maxOf(subsEndTime, ruleEndTime)
                val lockedBy = when {
                    ruleLocked -> 1
                    subsLocked -> 3 // Global rules skipped App level
                    else -> 0
                }
                val ruleKey = FocusLock.LockedRule(subs.id, group.group.key, null)
                val config = latestConfigByKey[Triple(subs.id, "", group.group.key)]

                RuleState(
                    group = group,
                    interceptConfig = config,
                    isSelectedForLock = selectedRules.contains(ruleKey),
                    isLocked = effectiveRuleLocked,
                    lockEndTime = effectiveRuleEndTime,
                    lockedBy = lockedBy
                )
            }

            val allG = if (gRuleStates.isEmpty()) true else gRuleStates.all { it.interceptConfig?.enabled == true }
            val allA = if (apps.isEmpty()) true else apps.all { it.allInterceptEnabled }
            val allSubsEnabled = (gRuleStates.isNotEmpty() || apps.isNotEmpty()) && allG && allA

            SubscriptionState(
                subsId = subs.id,
                subsName = subs.name,
                apps = apps,
                globalRules = gRuleStates,
                isLocked = subsLocked,
                lockEndTime = subsEndTime,
                allInterceptEnabled = allSubsEnabled
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleExpandSubs(subsId: Long) {
        val current = expandedSubs.value
        expandedSubs.value = if (current.contains(subsId)) current - subsId else current + subsId
    }

    fun toggleExpandApp(key: String) {
        val current = expandedApps.value
        expandedApps.value = if (current.contains(key)) current - key else current + key
    }

    fun toggleRuleSelection(group: ResolvedGroup) {
        val rule = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
        val current = selectedRulesSetFlow.value
        selectedRulesSetFlow.value = if (current.contains(rule)) {
            current - rule
        } else {
            current + rule
        }
    }

    // Lock a specific target (Subs, App, or Rule)
    fun lockTarget(targetType: Int, subsId: Long, appId: String? = null, groupKey: Int? = null) = viewModelScope.launchTry(Dispatchers.IO) {
        val durationMinutes = if (isCustomDuration) {
            val days = customDaysText.toIntOrNull() ?: 0
            val hours = customHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launchTry
        }

        val durationMillis = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()

        val existing = FocusLockUtils.allConstraintsFlow.value.find {
            it.targetType == targetType &&
            it.subsId == subsId &&
            it.appId == appId &&
            it.groupKey == groupKey
        }

        val newEndTime = if (existing != null && existing.lockEndTime > now) {
            existing.lockEndTime + durationMillis
        } else {
            now + durationMillis
        }

        val config = ConstraintConfig(
            id = existing?.id ?: 0,
            targetType = targetType,
            subsId = subsId,
            appId = appId,
            groupKey = groupKey,
            lockEndTime = newEndTime
        )
        
        DbSet.constraintConfigDao.insert(config)
        toast("锁定设置已更新")
    }

    fun updateInterceptConfig(subsId: Long, appId: String?, groupKey: Int, enabled: Boolean, cooldown: Int, message: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!enabled && FocusLockUtils.isRuleLocked(subsId, appId, groupKey)) {
            toast("当前规则已锁定，无法关闭自律模式")
            return@launch
        }
        val currentEnabled = resolveCurrentInterceptEnabled(subsId, appId, groupKey)
        if (shouldConsumeDisableQuota(currentEnabled = currentEnabled, requestedEnabled = enabled)) {
            val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
            if (!attempt.allowed) {
                toast(quotaBlockedToast(attempt.limit))
                return@launch
            }
        }
        val normalizedAppId = appId ?: ""
        DbSet.interceptConfigDao.delete(subsId, normalizedAppId, groupKey)
        val config = InterceptConfig(
            subsId = subsId,
            appId = normalizedAppId,
            groupKey = groupKey,
            enabled = enabled,
            cooldownSeconds = cooldown,
            message = message
        )
        DbSet.interceptConfigDao.insert(config)
    }

    fun batchUpdateInterceptConfig(subsId: Long, appId: String?, enabled: Boolean, cooldown: Int, message: String) = viewModelScope.launch(Dispatchers.IO) {
        val subState = subStatesFlow.value.find { it.subsId == subsId } ?: return@launch
        
        val targets = ArrayList<Triple<Long, String, Int>>() // subsId, appId, groupKey

        if (appId != null) {
            // App Level
            val appState = subState.apps.find { it.appId == appId } ?: return@launch
            targets.addAll(appState.rules.map { Triple(subsId, appId, it.group.group.key) })
        } else {
            // Subscription Level (All Apps + Global)
            // Global
            targets.addAll(subState.globalRules.map { Triple(subsId, "", it.group.group.key) })
            // Apps
            subState.apps.forEach { app ->
                targets.addAll(app.rules.map { Triple(subsId, app.appId, it.group.group.key) })
            }
        }

        var updatedCount = 0
        var skippedCount = 0
        if (!enabled && targets.any { (s, a, g) ->
                resolveCurrentInterceptEnabled(s, if (a.isEmpty()) null else a, g)
            }
        ) {
            val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
            if (!attempt.allowed) {
                toast(quotaBlockedToast(attempt.limit))
                return@launch
            }
        }

        targets.forEach { (s, a, g) ->
            if (!enabled && FocusLockUtils.isRuleLocked(s, if (a.isEmpty()) null else a, g)) {
                skippedCount++
                return@forEach
            }
            DbSet.interceptConfigDao.delete(s, a, g)
            val config = InterceptConfig(
                subsId = s,
                appId = a,
                groupKey = g,
                enabled = enabled,
                cooldownSeconds = cooldown,
                message = message
            )
            DbSet.interceptConfigDao.insert(config)
            updatedCount++
        }
        if (skippedCount > 0) {
            toast("更新 $updatedCount 条，跳过 $skippedCount 条(已锁定)")
        } else {
            toast("已批量更新配置")
        }
    }

    fun updateAutoReenableDailyDisableLimit(requestedLimit: Int, now: Long = System.currentTimeMillis()) {
        val normalizedLimit = AutoReenablePolicy.normalizeDailyDisableLimit(requestedLimit)
        val currentDayStartAt = AutoReenablePolicy.localDayStartEpochMs(now)
        storeFlow.update { settings ->
            val dayChanged = AutoReenablePolicy.shouldResetDailyCounter(
                dayStartAt = settings.autoReenableDailyDisableDayStartAt,
                now = now
            )
            val normalizedUsed = if (dayChanged) {
                0
            } else {
                settings.autoReenableDailyDisableUsed.coerceIn(0, normalizedLimit)
            }
            settings.copy(
                autoReenableDailyDisableLimit = normalizedLimit,
                autoReenableDailyDisableUsed = normalizedUsed,
                autoReenableDailyDisableDayStartAt = currentDayStartAt
            )
        }
        toast("已更新每日关闭限额：$normalizedLimit 次")
    }

    private fun resolveCurrentInterceptEnabled(subsId: Long, appId: String?, groupKey: Int): Boolean {
        val subState = subStatesFlow.value.find { it.subsId == subsId } ?: return false
        return if (appId.isNullOrEmpty()) {
            subState.globalRules.find { it.group.group.key == groupKey }?.interceptConfig?.enabled == true
        } else {
            subState.apps
                .find { it.appId == appId }
                ?.rules
                ?.find { it.group.group.key == groupKey }
                ?.interceptConfig
                ?.enabled == true
        }
    }

    fun updateAutoReenableInterval(requestedIntervalMinutes: Int, now: Long = System.currentTimeMillis()) {
        val settings = storeFlow.value
        val result = evaluateAutoReenableIntervalUpdate(
            currentIntervalMinutes = settings.autoReenableIntervalMinutes,
            lastChangedAt = settings.autoReenableIntervalChangedAt,
            requestedIntervalMinutes = requestedIntervalMinutes,
            now = now
        )

        if (!result.accepted) {
            toast("间隔冷却中，还需${formatCooldown(result.remainingCooldownMs)}后可修改")
            return
        }

        storeFlow.update {
            it.copy(
                autoReenableIntervalMinutes = result.intervalMinutes,
                autoReenableIntervalChangedAt = result.changedAt
            )
        }
        toast("已更新自动重开间隔：${result.intervalMinutes} 分钟")
    }

    companion object {
        fun shouldConsumeDisableQuota(currentEnabled: Boolean, requestedEnabled: Boolean): Boolean {
            return currentEnabled && !requestedEnabled
        }

        fun quotaBlockedToast(limit: Int): String {
            return "今日关闭次数已用完（$limit 次），将于明日 00:00 重置"
        }

        fun latestInterceptConfigByKey(interceptConfigs: List<InterceptConfig>): Map<Triple<Long, String, Int>, InterceptConfig> {
            val latest = LinkedHashMap<Triple<Long, String, Int>, InterceptConfig>()
            interceptConfigs.forEach { config ->
                val key = Triple(config.subsId, config.appId, config.groupKey)
                val current = latest[key]
                if (current == null || config.id > current.id) {
                    latest[key] = config
                }
            }
            return latest
        }

        fun evaluateAutoReenableUiState(
            intervalMinutes: Int,
            lastChangedAt: Long,
            scheduledNextEnforceAt: Long = 0L,
            dailyDisableLimit: Int = AutoReenablePolicy.MIN_DAILY_DISABLE_LIMIT,
            dailyDisableUsed: Int = 0,
            dailyDisableDayStartAt: Long = 0L,
            now: Long = System.currentTimeMillis(),
        ): AutoReenableUiState {
            val canEditInterval = AutoReenablePolicy.canChangeInterval(lastChangedAt, now)
            val nextEditableAt = if (lastChangedAt <= 0L) 0L else lastChangedAt + AutoReenablePolicy.CHANGE_COOLDOWN_MS
            val delayMs = AutoReenablePolicy.nextEnforceDelayMs(intervalMinutes)
            val normalizedLimit = AutoReenablePolicy.normalizeDailyDisableLimit(dailyDisableLimit)
            val currentDayStartAt = AutoReenablePolicy.localDayStartEpochMs(now)
            val dayChanged = AutoReenablePolicy.shouldResetDailyCounter(dailyDisableDayStartAt, now)
            val effectiveDayStartAt = if (dayChanged) currentDayStartAt else dailyDisableDayStartAt
            val normalizedUsed = if (dayChanged) {
                0
            } else {
                dailyDisableUsed.coerceIn(0, normalizedLimit)
            }
            return AutoReenableUiState(
                canEditInterval = canEditInterval,
                nextEditableAt = nextEditableAt,
                nextEnforceAt = scheduledNextEnforceAt.takeIf { it > now } ?: (now + delayMs),
                dailyDisableLimit = normalizedLimit,
                dailyDisableUsed = normalizedUsed,
                dailyDisableRemaining = (normalizedLimit - normalizedUsed).coerceAtLeast(0),
                nextDailyResetAt = effectiveDayStartAt + 24L * 60 * 60 * 1000
            )
        }

        fun evaluateAutoReenableIntervalUpdate(
            currentIntervalMinutes: Int,
            lastChangedAt: Long,
            requestedIntervalMinutes: Int,
            now: Long,
        ): AutoReenableIntervalUpdateResult {
            val normalized = AutoReenablePolicy.normalizeIntervalMinutes(requestedIntervalMinutes)
            val currentNormalized = AutoReenablePolicy.normalizeIntervalMinutes(currentIntervalMinutes)
            val remaining = (lastChangedAt + AutoReenablePolicy.CHANGE_COOLDOWN_MS - now).coerceAtLeast(0L)
            val canChange = normalized == currentNormalized ||
                AutoReenablePolicy.canChangeInterval(lastChangedAt, now)
            if (!canChange) {
                return AutoReenableIntervalUpdateResult(
                    accepted = false,
                    intervalMinutes = currentNormalized,
                    changedAt = lastChangedAt,
                    remainingCooldownMs = remaining
                )
            }
            return AutoReenableIntervalUpdateResult(
                accepted = true,
                intervalMinutes = normalized,
                changedAt = if (normalized == currentNormalized) lastChangedAt else now,
                remainingCooldownMs = 0L
            )
        }

        private fun formatCooldown(ms: Long): String {
            val minutes = (ms / 60_000L).coerceAtLeast(0L)
            val hours = minutes / 60
            val remainMinutes = minutes % 60
            return if (hours > 0) "${hours}小时${remainMinutes}分钟" else "${remainMinutes}分钟"
        }
    }
}
