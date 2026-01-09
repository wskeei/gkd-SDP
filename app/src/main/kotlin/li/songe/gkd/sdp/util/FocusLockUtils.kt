package li.songe.gkd.sdp.util

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.db.DbSet

object FocusLockUtils {
    val allConstraintsFlow: StateFlow<List<ConstraintConfig>> by lazy {
        DbSet.constraintConfigDao.queryAll()
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    fun isRuleLocked(subsId: Long, groupKey: Int, appId: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val constraints = allConstraintsFlow.value
        
        // 1. Check Rule Level
        if (constraints.any { 
            it.targetType == ConstraintConfig.TYPE_RULE_GROUP &&
            it.subsId == subsId && it.groupKey == groupKey && it.appId == appId &&
            it.isLocked
        }) return true
        
        // 2. Check App Level (if appId is present)
        if (appId != null && constraints.any {
            it.targetType == ConstraintConfig.TYPE_APP &&
            it.subsId == subsId && it.appId == appId &&
            it.isLocked
        }) return true
        
        // 3. Check Subscription Level
        if (constraints.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == subsId &&
            it.isLocked
        }) return true
        
        return false
    }

    fun isAppLocked(subsId: Long, appId: String): Boolean {
        val constraints = allConstraintsFlow.value
        
        // 1. Check Subscription Level
        if (constraints.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == subsId &&
            it.isLocked
        }) return true

        // 2. Check App Level
        if (constraints.any {
            it.targetType == ConstraintConfig.TYPE_APP &&
            it.subsId == subsId && it.appId == appId &&
            it.isLocked
        }) return true

        // 3. Check Any Rule in App Level
        if (constraints.any {
            it.targetType == ConstraintConfig.TYPE_RULE_GROUP &&
            it.subsId == subsId && it.appId == appId &&
            it.isLocked
        }) return true

        return false
    }

    fun isSubscriptionLocked(subsId: Long): Boolean {
        val constraints = allConstraintsFlow.value

        // 1. Check Subscription Level
        if (constraints.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == subsId &&
            it.isLocked
        }) return true

        // 2. Check Any App or Rule in Subscription Level
        if (constraints.any {
            it.subsId == subsId && it.isLocked
        }) return true

        return false
    }
}
