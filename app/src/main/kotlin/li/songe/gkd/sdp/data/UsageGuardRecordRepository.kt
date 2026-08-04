package li.songe.gkd.sdp.data

import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

/**
 * Keeps multi-step usage-record writes atomic without making Room process default DAO methods.
 * The DAO remains a thin SQL contract; this repository owns the domain transaction semantics.
 */
object UsageGuardRecordRepository {
    suspend fun closeRecordFromActiveUse(
        id: Long,
        endedAt: Long,
        endReason: Int,
    ): Int = DbSet.withTransaction {
        val dao = DbSet.usageGuardRecordDao
        dao.markUsageEnded(id, endedAt)
        dao.closeRecord(id, endedAt, endReason)
    }

    suspend fun insertRequestWithGap(
        record: UsageGuardRecord,
        replacedAt: Long,
    ): Long = DbSet.withTransaction {
        val dao = DbSet.usageGuardRecordDao
        val active = dao.getActiveRecord(record.appId)
        val gap = if (active != null) {
            dao.closeRecord(
                id = active.id,
                endedAt = replacedAt,
                endReason = UsageGuardRecord.END_REASON_REPLACED,
            )
            null
        } else {
            val previous = dao.getLatestRecord(record.appId)
            UsageRequestRhythmPolicy.gapMs(
                lastUsageEndedAt = previous?.lastUsageEndedAt,
                requestedAt = record.requestedAt,
            )
        }
        dao.insert(
            record.copy(
                lastUsageEndedAt = null,
                requestGapMs = gap,
            )
        )
    }
}
