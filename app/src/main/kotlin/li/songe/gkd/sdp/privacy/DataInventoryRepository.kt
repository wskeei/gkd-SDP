package li.songe.gkd.sdp.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.db.DbSet
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
class DataInventoryRepository(
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun inventory(): Map<DataCategory, DataDeletionCoordinator.CategoryStatus> {
        val activeLock = DbSet.digitalSelfDisciplineLockDao.hasAnyActiveLock(nowEpochMs())
        val activeUsage = DbSet.usageGuardRecordDao.countActive() > 0L
        val activeFocus = DbSet.focusSessionDao.getSessionNow()?.isActive == true
        val hasActiveSession = activeLock || activeUsage || activeFocus

        val nonAll = DataCategory.entries.filterNot { it == DataCategory.ALL_APP_DATA }
        val statuses = nonAll.associateWith { category ->
            when (category) {
                DataCategory.USAGE_REQUEST_HISTORY -> status(
                    count = DbSet.usageGuardRecordDao.count(),
                    hasActive = activeUsage,
                )
                DataCategory.FOCUS_SESSION_HISTORY -> status(
                    count = DbSet.focusSessionDao.count(),
                    hasActive = activeFocus,
                )
                DataCategory.INTERCEPTION_TRIGGER_RECORDS -> status(
                    count = DbSet.actionLogDao.count().first().toLong() +
                        DbSet.selfControlAttemptDao.countEvents() +
                        DbSet.selfControlAttemptDao.countAttempts(),
                )
                DataCategory.APP_INSTALL_MONITOR_HISTORY -> status(
                    count = DbSet.appInstallLogDao.count(),
                )
                DataCategory.SNAPSHOTS -> {
                    val files = snapshotFiles()
                    status(
                        count = DbSet.snapshotDao.count().first().toLong(),
                        bytes = files.sumOf { it.length() },
                    )
                }
                DataCategory.EVENT_ACTIVITY_LOGS -> status(
                    count = DbSet.activityLogDao.count().first().toLong() +
                        DbSet.a11yEventLogDao.count().first().toLong() +
                        DbSet.appVisitLogDao.count(),
                )
                DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> {
                    val files = diagnosticFiles()
                    status(
                        count = files.size.toLong(),
                        bytes = files.sumOf { it.length() },
                    )
                }
                DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> status(
                    count = DbSet.subsItemDao.queryAll().size.toLong(),
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
                hasActive = hasActiveSession,
            )
        )
    }

    suspend fun delete(category: DataCategory): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            when (category) {
                DataCategory.USAGE_REQUEST_HISTORY ->
                    DbSet.usageGuardRecordDao.deleteAll()

                DataCategory.FOCUS_SESSION_HISTORY ->
                    DbSet.focusSessionDao.deleteAll()

                DataCategory.INTERCEPTION_TRIGGER_RECORDS ->
                    DbSet.withTransaction {
                        DbSet.actionLogDao.deleteAll()
                        DbSet.selfControlAttemptDao.deleteAllEvents()
                        DbSet.selfControlAttemptDao.deleteAllAttempts()
                    }

                DataCategory.APP_INSTALL_MONITOR_HISTORY ->
                    DbSet.appInstallLogDao.deleteAll()

                DataCategory.SNAPSHOTS -> {
                    val snapshots = DbSet.snapshotDao.query().first()
                    if (snapshots.isNotEmpty()) {
                        SnapshotExt.deleteSnapshots(snapshots)
                    }
                }

                DataCategory.EVENT_ACTIVITY_LOGS ->
                    DbSet.withTransaction {
                        DbSet.activityLogDao.deleteAll()
                        DbSet.a11yEventLogDao.deleteAll()
                        DbSet.appVisitLogDao.deleteAll()
                    }

                DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> {
                    diagnosticFiles().forEach { it.delete() }
                }

                DataCategory.SUBSCRIPTIONS_RULES_CONFIG,
                DataCategory.SELF_CONTROL_CONFIG,
                DataCategory.ALL_APP_DATA,
                -> error("configuration deletion is routed through the settings reset flow")
            }
        }
    }

    private fun status(
        count: Long,
        bytes: Long = 0L,
        hasActive: Boolean = false,
    ) = DataDeletionCoordinator.CategoryStatus(
        recordCount = count.coerceAtLeast(0L),
        bytes = bytes.coerceAtLeast(0L),
        hasActiveSession = hasActive,
    )

    private fun snapshotFiles(): List<File> =
        li.songe.gkd.sdp.util.snapshotFolder.listFiles().orEmpty()
            .filter { it.isFile || it.isDirectory }

    private fun diagnosticFiles(): List<File> =
        buildList {
            addAll(logFolder.listFiles().orEmpty())
            addAll(crashFolder.listFiles().orEmpty())
        }
}
