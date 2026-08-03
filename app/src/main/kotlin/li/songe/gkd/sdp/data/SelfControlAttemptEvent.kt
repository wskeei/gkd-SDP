package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only, local history for self-control interception attempts.
 *
 * Usage-request history remains in [UsageGuardRecord]. This table only fills the historical
 * gap that the latest-state [SelfControlAttempt] table cannot represent.
 */
@Entity(
    tableName = "self_control_attempt_event",
    indices = [
        Index(value = ["event_key", "occurred_at"]),
        Index(value = ["event_kind", "occurred_at"]),
        Index(value = ["occurred_at"]),
    ],
)
data class SelfControlAttemptEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "event_kind") val eventKind: Int,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "subject_label") val subjectLabel: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "interval_ms") val intervalMs: Long?,
)
