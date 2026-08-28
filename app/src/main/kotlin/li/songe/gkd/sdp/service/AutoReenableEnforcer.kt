package li.songe.gkd.sdp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import kotlinx.coroutines.flow.update
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
                val delayMs = computeDelayMs(storeFlow.value.autoReenableIntervalMinutes)
                persistNextEnforceAt(computeNextEnforceAt(System.currentTimeMillis(), delayMs))
                delay(delayMs)
            }
        }
    }

    internal fun computeDelayMs(intervalMinutes: Int): Long {
        return AutoReenablePolicy.nextEnforceDelayMs(intervalMinutes)
    }

    internal fun computeNextEnforceAt(now: Long, delayMs: Long): Long {
        return now + delayMs
    }

    suspend fun enforceAll(): Int {
        return runEnableOperations(defaultEnableOperationEntries().map { it.second })
    }

    internal suspend fun runEnableOperations(operations: List<suspend () -> Int>): Int {
        var updatedCount = 0
        operations.forEach { operation ->
            updatedCount += operation()
        }
        return updatedCount
    }

    internal fun defaultOperationNames(): List<String> {
        return defaultEnableOperationEntries().map { it.first }
    }

    private fun defaultEnableOperationEntries(): List<Pair<String, suspend () -> Int>> {
        return listOf(
            "subs_item" to { DbSet.subsItemDao.enableAllDisabled() },
            "app_config" to { DbSet.appConfigDao.enableAllDisabled() },
            "app_group" to { DbSet.appGroupDao.enableAllDisabled() },
            "block_time_rule" to { DbSet.blockTimeRuleDao.enableAllDisabled() },
            "url_rule_group" to { DbSet.urlRuleGroupDao.enableAllDisabled() },
            "url_block_rule" to { DbSet.urlBlockRuleDao.enableAllDisabled() },
            "url_time_rule" to { DbSet.urlTimeRuleDao.enableAllDisabled() },
            "usage_guard_switch" to {
                val settings = storeFlow.value
                if (settings.usageGuardEnabled) {
                    0
                } else {
                    storeFlow.update { it.copy(usageGuardEnabled = true) }
                    1
                }
            },
            "accessibility_guard_switch" to {
                AccessibilityGuardController.autoReenableIfEligible()
            },
        )
    }

    private fun persistNextEnforceAt(nextEnforceAt: Long) {
        if (storeFlow.value.autoReenableNextEnforceAt == nextEnforceAt) return
        storeFlow.update { settings ->
            if (settings.autoReenableNextEnforceAt == nextEnforceAt) {
                settings
            } else {
                settings.copy(autoReenableNextEnforceAt = nextEnforceAt)
            }
        }
    }
}
