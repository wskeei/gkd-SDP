package li.songe.gkd.sdp.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.SnapshotExt
import li.songe.gkd.sdp.util.crashFolder
import li.songe.gkd.sdp.util.logFolder
import java.io.File

/**
 * Local privacy inventory and history deletion.
 *
 * This repository deliberately reports counts and byte estimates only. It
 * never includes request reasons, URLs, rule patterns, node text, contacts,
 * or other sensitive payloads in UI-facing summaries.
 */
internal class DataInventoryRepository(
    private val source: DataInventorySource = DatabaseDataInventorySource,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val snapshotDir: () -> List<File> = {
        li.songe.gkd.sdp.util.snapshotFolder.listFiles().orEmpty()
            .filter { it.isFile || it.isDirectory }
    },
    private val diagnosticsDir: () -> List<File> = {
        buildList {
            addAll(logFolder.listFiles().orEmpty())
            addAll(crashFolder.listFiles().orEmpty())
        }
    },
) {
    suspend fun inventory(): Map<DataCategory, DataDeletionCoordinator.CategoryStatus> {
        val activeLock = source.hasActiveLock(nowEpochMs())
        val activeUsage = source.usageRecordActiveCount() > 0L
        val activeFocus = source.focusSessionActive()
        val hasActiveSession = activeLock || activeUsage || activeFocus

        val nonAll = DataCategory.entries.filterNot { it == DataCategory.ALL_APP_DATA }
        val statuses = nonAll.associateWith { category ->
            when (category) {
                DataCategory.USAGE_REQUEST_HISTORY -> status(
                    count = source.usageRecordCount(),
                    hasActive = activeUsage,
                )
                DataCategory.FOCUS_SESSION_HISTORY -> status(
                    count = source.focusSessionCount(),
                    hasActive = activeFocus,
                )
                DataCategory.INTERCEPTION_TRIGGER_RECORDS -> status(
                    count = source.interceptionTriggerCount(),
                )
                DataCategory.APP_INSTALL_MONITOR_HISTORY -> status(
                    count = source.appInstallCount(),
                )
                DataCategory.SNAPSHOTS -> status(
                    count = source.snapshotCount(),
                    bytes = snapshotDir().sumOf { it.length() },
                )
                DataCategory.EVENT_ACTIVITY_LOGS -> status(
                    count = source.activityLogCount() +
                        source.a11yEventLogCount() +
                        source.appVisitCount(),
                )
                DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> {
                    val files = diagnosticsDir()
                    status(
                        count = files.size.toLong(),
                        bytes = files.sumOf { it.length() },
                    )
                }
                DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> status(
                    count = source.subsItemCount(),
                    hasActive = hasActiveSession,
                )
                DataCategory.SELF_CONTROL_CONFIG -> status(
                    count = 0L,
                    hasActive = hasActiveSession,
                )

                DataCategory.ALL_APP_DATA -> error("computed after non-all categories")
            }
        }
        return statuses + (
            DataCategory.ALL_APP_DATA to status(
                count = statuses.values.sumOf { it.recordCount },
                bytes = statuses.values.mapNotNull { it.bytes }.takeIf { it.isNotEmpty() }?.sum(),
                hasActive = hasActiveSession,
            )
        )
    }

    suspend fun delete(category: DataCategory): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            when (category) {
                DataCategory.USAGE_REQUEST_HISTORY ->
                    source.deleteUsageRecords()

                DataCategory.FOCUS_SESSION_HISTORY ->
                    source.deleteFocusSessions()

                DataCategory.INTERCEPTION_TRIGGER_RECORDS ->
                    source.deleteInterceptionTriggers()

                DataCategory.APP_INSTALL_MONITOR_HISTORY ->
                    source.deleteAppInstallHistory()

                DataCategory.SNAPSHOTS ->
                    source.deleteSnapshotRowsAndFiles()

                DataCategory.EVENT_ACTIVITY_LOGS ->
                    source.deleteEventLogs()

                DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> {
                    diagnosticsDir().forEach { it.delete() }
                }

                DataCategory.SUBSCRIPTIONS_RULES_CONFIG ->
                    source.deleteSubscriptionsConfig()

                DataCategory.SELF_CONTROL_CONFIG ->
                    source.deleteSelfControlConfig()

                DataCategory.ALL_APP_DATA ->
                    DataCategory.entries
                        .filterNot { it == DataCategory.ALL_APP_DATA }
                        .forEach { delete(it) }
            }
        }
    }

    private fun status(
        count: Long,
        bytes: Long? = null,
        hasActive: Boolean = false,
    ) = DataDeletionCoordinator.CategoryStatus(
        recordCount = count.coerceAtLeast(0L),
        bytes = bytes?.coerceAtLeast(0L),
        hasActiveSession = hasActive,
    )
}

private object DatabaseDataInventorySource : DataInventorySource {
    override suspend fun hasActiveLock(nowEpochMs: Long): Boolean =
        DbSet.digitalSelfDisciplineLockDao.hasAnyActiveLock(nowEpochMs)

    override suspend fun usageRecordCount(): Long =
        DbSet.usageGuardRecordDao.count()

    override suspend fun usageRecordActiveCount(): Long =
        DbSet.usageGuardRecordDao.countActive()

    override suspend fun focusSessionActive(): Boolean =
        DbSet.focusSessionDao.getSessionNow()?.isActive == true

    override suspend fun focusSessionCount(): Long =
        DbSet.focusSessionDao.count()

    override suspend fun interceptionTriggerCount(): Long =
        DbSet.actionLogDao.count().first().toLong() +
            DbSet.selfControlAttemptDao.countEvents() +
            DbSet.selfControlAttemptDao.countAttempts()

    override suspend fun appInstallCount(): Long =
        DbSet.appInstallLogDao.count()

    override suspend fun snapshotCount(): Long =
        DbSet.snapshotDao.count().first().toLong()

    override suspend fun activityLogCount(): Long =
        DbSet.activityLogDao.count().first().toLong()

    override suspend fun a11yEventLogCount(): Long =
        DbSet.a11yEventLogDao.count().first().toLong()

    override suspend fun appVisitCount(): Long =
        DbSet.appVisitLogDao.count()

    override suspend fun subsItemCount(): Long =
        DbSet.subsItemDao.queryAll().size.toLong()

    override suspend fun deleteUsageRecords() {
        DbSet.usageGuardRecordDao.deleteAll()
    }

    override suspend fun deleteFocusSessions() {
        DbSet.focusSessionDao.deleteAll()
    }

    override suspend fun deleteInterceptionTriggers() {
        DbSet.withTransaction {
            DbSet.actionLogDao.deleteAll()
            DbSet.selfControlAttemptDao.deleteAllEvents()
            DbSet.selfControlAttemptDao.deleteAllAttempts()
        }
    }

    override suspend fun deleteAppInstallHistory() {
        DbSet.appInstallLogDao.deleteAll()
    }

    override suspend fun deleteSnapshotRowsAndFiles() {
        val snapshots = DbSet.snapshotDao.query().first()
        if (snapshots.isNotEmpty()) {
            SnapshotExt.deleteSnapshots(snapshots)
        }
    }

    override suspend fun deleteEventLogs() {
        DbSet.withTransaction {
            DbSet.activityLogDao.deleteAll()
            DbSet.a11yEventLogDao.deleteAll()
            DbSet.appVisitLogDao.deleteAll()
        }
    }

    override suspend fun deleteSubscriptionsConfig() {
        DbSet.withTransaction {
            DbSet.subsItemDao.deleteAll()
            DbSet.subsConfigDao.deleteAll()
            DbSet.categoryConfigDao.deleteAll()
            DbSet.appConfigDao.deleteAll()
            DbSet.interceptConfigDao.deleteAll()
            DbSet.constraintConfigDao.deleteAll()
            DbSet.urlBlockRuleDao.deleteAll()
            DbSet.browserConfigDao.deleteAll()
            DbSet.focusRuleDao.deleteAll()
            DbSet.appGroupDao.deleteAll()
            DbSet.blockTimeRuleDao.deleteAll()
            DbSet.appBlockerLockDao.deleteAll()
            DbSet.wechatContactDao.deleteAll()
            DbSet.urlRuleGroupDao.deleteAll()
            DbSet.urlTimeRuleDao.deleteAll()
            DbSet.urlBlockerLockDao.deleteAll()
            DbSet.focusLockDao.deleteAll()
            DbSet.monitoredAppDao.deleteAll()
        }
    }

    override suspend fun deleteSelfControlConfig() {
        DbSet.withTransaction {
            storeFlow.value = DataMutationCoordinator.resetSelfControlConfig(storeFlow.value)
            DbSet.usageGuardAppProfileDao.deleteAll()
            DbSet.usageGuardTagDao.deleteCustomTags()
        }
    }
}
