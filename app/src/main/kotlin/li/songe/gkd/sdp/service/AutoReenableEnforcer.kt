package li.songe.gkd.sdp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.AutoReenablePolicy
import li.songe.gkd.sdp.util.LogUtils

object AutoReenableEnforcer {
    private var loopJob: Job? = null

    @Synchronized
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = appScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    enforceAll()
                } catch (e: Throwable) {
                    LogUtils.d(e)
                }
                delay(computeDelayMs(storeFlow.value.autoReenableIntervalMinutes))
            }
        }
    }

    internal fun computeDelayMs(intervalMinutes: Int): Long {
        return AutoReenablePolicy.nextEnforceDelayMs(intervalMinutes)
    }

    suspend fun enforceAll(): Int {
        return runEnableOperations(defaultEnableOperations())
    }

    internal suspend fun runEnableOperations(operations: List<suspend () -> Int>): Int {
        var updatedCount = 0
        operations.forEach { operation ->
            updatedCount += operation()
        }
        return updatedCount
    }

    private fun defaultEnableOperations(): List<suspend () -> Int> {
        return listOf(
            { DbSet.subsItemDao.enableAllDisabled() },
            { DbSet.appGroupDao.enableAllDisabled() },
            { DbSet.blockTimeRuleDao.enableAllDisabled() },
            { DbSet.urlRuleGroupDao.enableAllDisabled() },
            { DbSet.urlBlockRuleDao.enableAllDisabled() },
            { DbSet.urlTimeRuleDao.enableAllDisabled() },
        )
    }
}
