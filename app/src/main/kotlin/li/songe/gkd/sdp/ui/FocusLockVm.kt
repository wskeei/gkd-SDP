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
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.toast

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
    val lockEndTime: Long
)

data class SubscriptionState(
    val subsId: Long,
    val subsName: String,
    val apps: List<AppState>,
    val globalRules: List<RuleState>,
    val isLocked: Boolean,
    val lockEndTime: Long
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

                val ruleStates = groups.filter { it.enable }.map { group ->
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
                    val config = interceptConfigs.find { it.subsId == subs.id && it.appId == app.id && it.groupKey == group.group.key }
                    
                    RuleState(
                        group = group,
                        interceptConfig = config,
                        isSelectedForLock = selectedRules.contains(ruleKey),
                        isLocked = effectiveRuleLocked,
                        lockEndTime = effectiveRuleEndTime,
                        lockedBy = lockedBy
                    )
                }
                AppState(
                    appId = app.id,
                    appName = app.name ?: app.id,
                    rules = ruleStates,
                    isLocked = effectiveAppLocked,
                    lockEndTime = effectiveAppEndTime
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
                val config = interceptConfigs.find { it.subsId == subs.id && it.appId == "" && it.groupKey == group.group.key }

                RuleState(
                    group = group,
                    interceptConfig = config,
                    isSelectedForLock = selectedRules.contains(ruleKey),
                    isLocked = effectiveRuleLocked,
                    lockEndTime = effectiveRuleEndTime,
                    lockedBy = lockedBy
                )
            }

            SubscriptionState(
                subsId = subs.id,
                subsName = subs.name,
                apps = apps,
                globalRules = gRuleStates,
                isLocked = subsLocked,
                lockEndTime = subsEndTime
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
        if (!enabled && FocusLockUtils.isRuleLocked(subsId, groupKey, appId)) {
            toast("当前规则已锁定，无法关闭自律模式")
            return@launch
        }
        val config = InterceptConfig(
            subsId = subsId,
            appId = appId ?: "",
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

        targets.forEach { (s, a, g) ->
            if (!enabled && FocusLockUtils.isRuleLocked(s, g, if (a.isEmpty()) null else a)) {
                skippedCount++
                return@forEach
            }
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
}
