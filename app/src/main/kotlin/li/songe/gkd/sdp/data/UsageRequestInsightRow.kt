package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo

/** Minimal Room projection used by the request insight chart. */
data class UsageRequestInsightRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "requested_at") val requestedAt: Long,
    @ColumnInfo(name = "requested_duration_minutes") val requestedDurationMinutes: Int,
    @ColumnInfo(name = "last_usage_ended_at") val lastUsageEndedAt: Long?,
    @ColumnInfo(name = "request_gap_ms") val requestGapMs: Long?,
)
