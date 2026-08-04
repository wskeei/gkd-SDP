package li.songe.gkd.sdp.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.db.DbSet

/**
 * The single writer for selector action history. It keeps execution and interception outcomes
 * in one bounded table without touching rule runtime counters.
 */
class RuleTriggerLogRepository(
    private val sink: Sink,
) {
    interface Sink {
        suspend fun insertBounded(log: ActionLog): Long

        suspend fun deleteKeepLatest() = Unit
    }

    private val mutex = Mutex()

    suspend fun recordExecuted(
        snapshot: SelectorRuleSnapshot,
        ctime: Long = snapshot.matchedAt,
    ): Long = insert(snapshot.toActionLog(ActionLog.OUTCOME_ACTION_EXECUTED, ctime))

    suspend fun recordIntercepted(
        snapshot: SelectorRuleSnapshot,
        ctime: Long = snapshot.matchedAt,
    ): Long = insert(snapshot.toActionLog(ActionLog.OUTCOME_INTERCEPTED, ctime))

    private suspend fun insert(log: ActionLog): Long = mutex.withLock {
        val rowId = sink.insertBounded(log)
        if (rowId > 0L && rowId % ActionLog.PRUNE_EVERY_ROWS == 0L) {
            sink.deleteKeepLatest()
        }
        rowId
    }

    private class DaoSink(
        val dao: ActionLog.ActionLogDao,
    ) : Sink {
        override suspend fun insertBounded(log: ActionLog): Long =
            dao.insert(log).single()

        override suspend fun deleteKeepLatest() {
            dao.deleteKeepLatest()
        }
    }

    companion object {
        private val default by lazy {
            RuleTriggerLogRepository(DaoSink(DbSet.actionLogDao))
        }

        fun fromDb(): RuleTriggerLogRepository = default
    }
}
