package li.songe.gkd.util

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.FocusLock
import li.songe.gkd.db.DbSet

object FocusLockUtils {
    val activeLockFlow: StateFlow<FocusLock?> by lazy {
        DbSet.focusLockDao.queryActive()
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    fun isRuleLocked(subsId: Long, groupKey: Int, appId: String? = null): Boolean {
        val lock = activeLockFlow.value ?: return false
        if (!lock.isActive) return false

        val lockedRules = json.decodeFromString<List<FocusLock.LockedRule>>(lock.lockedRules)
        return lockedRules.any {
            it.subsId == subsId && it.groupKey == groupKey && it.appId == appId
        }
    }

    suspend fun createLock(
        rules: List<FocusLock.LockedRule>,
        durationMinutes: Int
    ): Long {
        val now = System.currentTimeMillis()
        val lock = FocusLock(
            startTime = now,
            endTime = now + durationMinutes * 60 * 1000L,
            lockedRules = json.encodeToString(rules)
        )
        return DbSet.focusLockDao.insert(lock).first()
    }
}
