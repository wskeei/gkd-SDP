package li.songe.gkd.sdp.util

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.db.DbSet

object FocusLockUtils {
    // 网址拦截功能使用的特殊 subsId
    const val URL_BLOCKER_SUBS_ID = -100L

    val allConstraintsFlow: StateFlow<List<ConstraintConfig>> by lazy {
        DbSet.constraintConfigDao.queryAll()
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    /**
     * 检查网址拦截功能是否被锁定
     */
    fun isUrlBlockerLocked(): Boolean {
        return allConstraintsFlow.value.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == URL_BLOCKER_SUBS_ID &&
            it.isLocked
        }
    }

    fun isAntiUninstallLocked(): Boolean {
        return allConstraintsFlow.value.any {
            it.targetType == ConstraintConfig.TYPE_ANTI_UNINSTALL &&
            it.isLocked
        }
    }

    fun isRuleLocked(subsId: Long, appId: String?, groupKey: Int): Boolean {
        return allConstraintsFlow.value.any {
            it.targetType == ConstraintConfig.TYPE_RULE_GROUP &&
            it.subsId == subsId &&
            it.appId == appId &&
            it.groupKey == groupKey &&
            it.isLocked
        }
    }

    fun isAppLocked(subsId: Long, appId: String): Boolean {
        return allConstraintsFlow.value.any {
            it.targetType == ConstraintConfig.TYPE_APP &&
            it.subsId == subsId &&
            it.appId == appId &&
            it.isLocked
        }
    }

    fun isSubscriptionLocked(subsId: Long): Boolean {
        return allConstraintsFlow.value.any {
            it.targetType == ConstraintConfig.TYPE_SUBSCRIPTION &&
            it.subsId == subsId &&
            it.isLocked
        }
    }
}
