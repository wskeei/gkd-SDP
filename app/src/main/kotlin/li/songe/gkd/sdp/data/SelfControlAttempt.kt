package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import java.math.BigDecimal

@Entity(tableName = "self_control_attempt")
data class SelfControlAttempt(
    @PrimaryKey
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "event_kind") val eventKind: Int,
    @ColumnInfo(name = "last_occurred_at") val lastOccurredAt: Long,
) {
    companion object {
        const val KIND_APP_BLOCKER = 1
        const val KIND_SELECTOR_INTERCEPT = 2
        const val KIND_URL_INTERCEPT = 3

        const val RETENTION_DAYS = 90L
        const val MAX_EVENT_ROWS = 10_000
        const val OVERLAY_HISTORY_LIMIT = 5
        const val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1_000L

        internal fun monotonicAnchor(previousAt: Long?, currentAt: Long): Long {
            return maxOf(previousAt ?: currentAt, currentAt)
        }
    }

    data class RecordedAttemptInsight(
        val previousOccurredAt: Long?,
        val recentCompletedIntervalsMs: List<Long> = emptyList(),
        val samples: List<SelfControlInsightWindowPolicy.IntervalSample> = emptyList(),
        val currentEventId: Long? = null,
    )

    @Dao
    interface SelfControlAttemptDao {
        @Query("SELECT * FROM self_control_attempt WHERE event_key = :eventKey LIMIT 1")
        suspend fun getByKey(eventKey: String): SelfControlAttempt?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(attempt: SelfControlAttempt)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertEvent(event: SelfControlAttemptEvent): Long

        @Query(
            """
            SELECT interval_ms FROM self_control_attempt_event
            WHERE event_key = :eventKey
              AND interval_ms IS NOT NULL
              AND interval_ms >= 0
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit
            """
        )
        suspend fun queryRecentCompletedIntervals(eventKey: String, limit: Int): List<Long>

        @Query(
            """
            SELECT * FROM self_control_attempt_event
            WHERE event_key = :eventKey
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit
            """
        )
        suspend fun queryRecentEvents(eventKey: String, limit: Int): List<SelfControlAttemptEvent>

        @Query(
            """
            SELECT * FROM self_control_attempt_event
            WHERE event_key = :eventKey
              AND occurred_at >= :startAt
              AND occurred_at <= :endAt
              AND interval_ms IS NOT NULL
              AND interval_ms >= 0
            ORDER BY occurred_at ASC, id ASC
            """
        )
        suspend fun queryByEventKeyAndOccurredAtRange(
            eventKey: String,
            startAt: Long,
            endAt: Long,
        ): List<SelfControlAttemptEvent>

        @Query(
            """
            SELECT * FROM self_control_attempt_event
            WHERE occurred_at >= :startAt AND occurred_at < :endAt
            ORDER BY occurred_at ASC, id ASC
            """
        )
        fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>>

        @Query("SELECT COUNT(*) FROM self_control_attempt_event")
        suspend fun countEvents(): Int

        @Query("DELETE FROM self_control_attempt_event WHERE occurred_at < :cutoffAt")
        suspend fun deleteEventsBefore(cutoffAt: Long): Int

        @Query(
            """
            DELETE FROM self_control_attempt_event
            WHERE id IN (
                SELECT id FROM self_control_attempt_event
                ORDER BY occurred_at ASC, id ASC
                LIMIT :limit
            )
            """
        )
        suspend fun deleteOldestEvents(limit: Int): Int

        @Query("DELETE FROM self_control_attempt WHERE last_occurred_at < :cutoffAt")
        suspend fun deleteAttemptsBefore(cutoffAt: Long): Int

        @Query("SELECT COUNT(*) FROM self_control_attempt")
        suspend fun countAttempts(): Int

        @Query(
            """
            DELETE FROM self_control_attempt
            WHERE event_key IN (
                SELECT event_key FROM self_control_attempt
                ORDER BY last_occurred_at ASC, event_key ASC
                LIMIT :limit
            )
            """
        )
        suspend fun deleteOldestAttempts(limit: Int): Int

        @Transaction
        suspend fun recordAndGetPrevious(attempt: SelfControlAttempt): Long? {
            val previous = getByKey(attempt.eventKey)?.lastOccurredAt
            insert(attempt)
            return previous
        }

        @Transaction
        suspend fun recordEventAndGetInsight(event: SelfControlAttemptEvent): RecordedAttemptInsight {
            val previous = getByKey(event.eventKey)
            val intervalMs = previous?.lastOccurredAt?.let { previousAt ->
                event.occurredAt.takeIf { it >= previousAt }?.minus(previousAt)
            }
            val currentEventId = insertEvent(event.copy(intervalMs = intervalMs))
            insert(
                SelfControlAttempt(
                    eventKey = event.eventKey,
                    eventKind = event.eventKind,
                    // A clock rollback must not move the per-key anchor backwards. The
                    // rollback event is still retained for audit/history, but the next
                    // valid event must be measured from the last monotonic anchor.
                    lastOccurredAt = monotonicAnchor(previous?.lastOccurredAt, event.occurredAt),
                ),
            )

            val cutoffAt = BigDecimal.valueOf(event.occurredAt)
                .subtract(BigDecimal.valueOf(RETENTION_MS))
                .max(BigDecimal.ZERO)
                .toLong()
            deleteEventsBefore(cutoffAt)
            val total = countEvents()
            if (total > MAX_EVENT_ROWS) {
                deleteOldestEvents(total - MAX_EVENT_ROWS)
            }

            deleteAttemptsBefore(cutoffAt)
            val attemptTotal = countAttempts()
            if (attemptTotal > MAX_EVENT_ROWS) {
                deleteOldestAttempts(attemptTotal - MAX_EVENT_ROWS)
            }

            val samples = queryByEventKeyAndOccurredAtRange(
                eventKey = event.eventKey,
                startAt = cutoffAt,
                endAt = event.occurredAt,
            ).map { row ->
                SelfControlInsightWindowPolicy.IntervalSample(
                    id = row.id,
                    occurredAtEpochMs = row.occurredAt,
                    gapMs = row.intervalMs,
                    requestedDurationMinutes = null,
                )
            }

            return RecordedAttemptInsight(
                previousOccurredAt = previous?.lastOccurredAt,
                recentCompletedIntervalsMs = samples.mapNotNull { it.gapMs }
                    .takeLast(OVERLAY_HISTORY_LIMIT),
                samples = samples,
                currentEventId = currentEventId,
            )
        }
    }
}
