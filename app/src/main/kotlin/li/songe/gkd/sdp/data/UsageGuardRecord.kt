package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.sdp.util.UsageGuardPolicy

@Entity(
    tableName = "usage_guard_record",
    indices = [
        Index(value = ["app_id", "ended_at"]),
        Index(value = ["app_id", "requested_at"]),
    ],
)
data class UsageGuardRecord(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "tag_names") val tagNames: List<String>,
    @ColumnInfo(name = "reason_text") val reasonText: String,
    @ColumnInfo(name = "grant_mode") val grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
    @ColumnInfo(name = "requested_duration_minutes") val requestedDurationMinutes: Int,
    @ColumnInfo(name = "requested_at") val requestedAt: Long,
    @ColumnInfo(name = "granted_at") val grantedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long = 0L,
    @ColumnInfo(name = "end_reason") val endReason: Int = END_REASON_ACTIVE,
    @ColumnInfo(name = "last_usage_ended_at") val lastUsageEndedAt: Long? = null,
    @ColumnInfo(name = "request_gap_ms") val requestGapMs: Long? = null,
) {
    companion object {
        const val END_REASON_ACTIVE = 0
        const val END_REASON_EXPIRED = 1
        const val END_REASON_LEFT_APP = 2
        const val END_REASON_REPLACED = 3
        const val END_REASON_HOME_BUTTON = 4
        const val END_REASON_USER_TERMINATED = 5
    }

    @Dao
    interface UsageGuardRecordDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(record: UsageGuardRecord): Long

        @Query("SELECT * FROM usage_guard_record WHERE app_id = :appId AND ended_at = 0 ORDER BY id DESC LIMIT 1")
        suspend fun getActiveRecord(appId: String): UsageGuardRecord?

        @Query(
            """
            SELECT * FROM usage_guard_record
            WHERE app_id = :appId
            ORDER BY requested_at DESC, id DESC
            LIMIT 1
            """
        )
        suspend fun getLatestRecord(appId: String): UsageGuardRecord?

        @Query(
            """
            SELECT * FROM usage_guard_record
            WHERE app_id = :appId
            ORDER BY requested_at DESC, id DESC
            LIMIT :limit
            """
        )
        suspend fun queryRecentRecords(appId: String, limit: Int): List<UsageGuardRecord>

        @Query(
            """
            SELECT * FROM usage_guard_record
            WHERE app_id = :appId
              AND (
                requested_at < :requestedAt
                OR (requested_at = :requestedAt AND id < :id)
              )
            ORDER BY requested_at DESC, id DESC
            LIMIT 1
            """
        )
        suspend fun getPreviousRecord(
            appId: String,
            requestedAt: Long,
            id: Long,
        ): UsageGuardRecord?

        @Query(
            """
            SELECT *
            FROM usage_guard_record
            WHERE app_id = :appId
              AND requested_at >= :startAt
              AND requested_at <= :endAt
            ORDER BY requested_at ASC, id ASC
            """
        )
        suspend fun queryRecordsByAppAndRequestedAtRange(
            appId: String,
            startAt: Long,
            endAt: Long,
        ): List<UsageGuardRecord>

        @Query("SELECT * FROM usage_guard_record ORDER BY id DESC LIMIT :limit")
        fun queryLatest(limit: Int = 100): Flow<List<UsageGuardRecord>>

        @Query(
            """
            SELECT * FROM usage_guard_record
            WHERE requested_at >= :startAt AND requested_at < :endAt
            ORDER BY requested_at DESC
            """
        )
        fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>>

        @Query("UPDATE usage_guard_record SET ended_at = :endedAt, end_reason = :endReason WHERE id = :id")
        suspend fun closeRecord(id: Long, endedAt: Long, endReason: Int): Int

        @Query(
            """
            UPDATE usage_guard_record
            SET last_usage_ended_at = :endedAt
            WHERE id = :id
              AND ended_at = 0
              AND (
                    last_usage_ended_at IS NULL
                    OR last_usage_ended_at <= :endedAt
              )
            """
        )
        suspend fun markUsageEnded(id: Long, endedAt: Long): Int

        @Query(
            """
            UPDATE usage_guard_record
            SET last_usage_ended_at = NULL
            WHERE id = :id AND ended_at = 0
            """
        )
        suspend fun markUsageStarted(id: Long): Int

        @Transaction
        suspend fun closeRecordFromActiveUse(
            id: Long,
            endedAt: Long,
            endReason: Int,
        ): Int {
            markUsageEnded(id, endedAt)
            return closeRecord(id, endedAt, endReason)
        }

        @Transaction
        suspend fun insertRequestWithGap(
            record: UsageGuardRecord,
            replacedAt: Long,
        ): Long {
            val active = getActiveRecord(record.appId)
            val gap = if (active != null) {
                closeRecord(
                    id = active.id,
                    endedAt = replacedAt,
                    endReason = UsageGuardRecord.END_REASON_REPLACED,
                )
                null
            } else {
                val previous = getLatestRecord(record.appId)
                previous?.lastUsageEndedAt?.let { lastUsageEndedAt ->
                    if (lastUsageEndedAt >= 0L &&
                        record.requestedAt >= lastUsageEndedAt
                    ) {
                        record.requestedAt - lastUsageEndedAt
                    } else {
                        null
                    }
                }
            }
            return insert(
                record.copy(
                    lastUsageEndedAt = null,
                    requestGapMs = gap,
                )
            )
        }

        @Query("UPDATE usage_guard_record SET end_reason = :endReason WHERE id = :id")
        suspend fun updateEndReason(id: Long, endReason: Int): Int
    }
}
