package li.songe.gkd.sdp.data

import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

internal interface UsageGuardRecordSource {
    suspend fun getActiveRecord(appId: String): UsageGuardRecord?
    suspend fun getLatestRecord(appId: String): UsageGuardRecord?
    suspend fun insert(record: UsageGuardRecord): Long
    suspend fun closeRecord(id: Long, endedAt: Long, endReason: Int): Int
    suspend fun closeRecordFromActiveUse(id: Long, endedAt: Long, endReason: Int): Int
}

internal interface UsageGuardRecordTransaction {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

internal object UsageGuardRecordRepositoryTestHooks {
    @Volatile
    var source: UsageGuardRecordSource? = null

    @Volatile
    var transaction: UsageGuardRecordTransaction? = null
}

/**
 * Keeps multi-step usage-record writes atomic without making Room process default DAO methods.
 * The DAO remains a thin SQL contract; this repository owns the domain transaction semantics.
 */
object UsageGuardRecordRepository {
    private val source: UsageGuardRecordSource
        get() = UsageGuardRecordRepositoryTestHooks.source ?: DatabaseUsageGuardRecordSource

    private val transaction: UsageGuardRecordTransaction
        get() = UsageGuardRecordRepositoryTestHooks.transaction ?: DatabaseUsageGuardRecordTransaction

    suspend fun closeRecordFromActiveUse(
        id: Long,
        endedAt: Long,
        endReason: Int,
    ): Int = transaction.withTransaction {
        source.closeRecordFromActiveUse(id, endedAt, endReason)
    }

    suspend fun insertRequestWithGap(
        record: UsageGuardRecord,
        replacedAt: Long,
    ): Long = transaction.withTransaction {
        val active = source.getActiveRecord(record.appId)
        val gap = if (active != null) {
            source.closeRecord(
                id = active.id,
                endedAt = replacedAt,
                endReason = UsageGuardRecord.END_REASON_REPLACED,
            )
            null
        } else {
            val previous = source.getLatestRecord(record.appId)
            UsageRequestRhythmPolicy.gapMs(
                lastUsageEndedAt = previous?.lastUsageEndedAt,
                requestedAt = record.requestedAt,
            )
        }
        source.insert(
            record.copy(
                lastUsageEndedAt = null,
                requestGapMs = gap,
            )
        )
    }
}

private object DatabaseUsageGuardRecordSource : UsageGuardRecordSource {
    override suspend fun getActiveRecord(appId: String): UsageGuardRecord? =
        DbSet.usageGuardRecordDao.getActiveRecord(appId)

    override suspend fun getLatestRecord(appId: String): UsageGuardRecord? =
        DbSet.usageGuardRecordDao.getLatestRecord(appId)

    override suspend fun insert(record: UsageGuardRecord): Long =
        DbSet.usageGuardRecordDao.insert(record)

    override suspend fun closeRecord(id: Long, endedAt: Long, endReason: Int): Int =
        DbSet.usageGuardRecordDao.closeRecord(id, endedAt, endReason)

    override suspend fun closeRecordFromActiveUse(
        id: Long,
        endedAt: Long,
        endReason: Int,
    ): Int = DbSet.usageGuardRecordDao.closeRecordFromActiveUse(id, endedAt, endReason)
}

private object DatabaseUsageGuardRecordTransaction : UsageGuardRecordTransaction {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        DbSet.withTransaction(block)
}
