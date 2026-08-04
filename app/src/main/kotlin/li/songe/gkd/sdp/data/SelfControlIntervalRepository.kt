package li.songe.gkd.sdp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy

/**
 * Adapts Room records to the interval domain without exposing database entities to UI code.
 */
class SelfControlIntervalRepository(
    private val usageRecords: UsageRecordSource,
    private val attemptEvents: AttemptEventSource,
) {
    interface UsageRecordSource {
        suspend fun queryRecentRecords(appId: String, limit: Int): List<UsageGuardRecord>

        fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>>

        suspend fun queryInsightRows(
            appId: String,
            startAt: Long,
            endAt: Long,
        ): List<UsageRequestInsightRow> = queryRecentRecords(appId, 10_000)
            .filter { it.requestedAt in startAt..endAt }
            .sortedWith(compareBy<UsageGuardRecord> { it.requestedAt }.thenBy { it.id })
            .map {
                UsageRequestInsightRow(
                    id = it.id,
                    requestedAt = it.requestedAt,
                    requestedDurationMinutes = it.requestedDurationMinutes,
                    lastUsageEndedAt = it.lastUsageEndedAt,
                    requestGapMs = it.requestGapMs,
                )
            }

        suspend fun getLatestInsightRow(appId: String): UsageRequestInsightRow? =
            queryRecentRecords(appId, 10_000)
                .maxWithOrNull(compareBy<UsageGuardRecord> { it.requestedAt }.thenBy { it.id })
                ?.let {
                    UsageRequestInsightRow(
                        id = it.id,
                        requestedAt = it.requestedAt,
                        requestedDurationMinutes = it.requestedDurationMinutes,
                        lastUsageEndedAt = it.lastUsageEndedAt,
                        requestGapMs = it.requestGapMs,
                    )
                }
    }

    interface AttemptEventSource {
        suspend fun recordEventAndGetInsight(
            event: SelfControlAttemptEvent,
        ): SelfControlAttempt.RecordedAttemptInsight

        fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>>

        suspend fun queryByEventKeyAndOccurredAtRange(
            eventKey: String,
            startAt: Long,
            endAt: Long,
        ): List<SelfControlAttemptEvent> = emptyList()
    }

    data class AttemptDescriptor(
        val eventKey: String,
        val eventKind: Int,
        val subjectId: String,
        val subjectLabel: String,
    )

    data class UsageRequestOverlay(
        val latestRequestedAt: Long?,
        val recentCompletedIntervalsMs: List<Long>,
    ) {
        val hasHistory: Boolean
            get() = latestRequestedAt != null
    }

    enum class UsageGapAnchorStatus {
        NoPreviousRequest,
        Available,
        MissingActualEnd,
    }

    data class UsageRequestOverlayData(
        val insightAnchorAt: Long,
        val latestRequestedAt: Long?,
        val anchorStatus: UsageGapAnchorStatus,
        val previousLastUsageEndedAt: Long?,
        val samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
    )

    data class InterceptOverlayData(
        val insightAnchorAt: Long,
        val samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
        val currentEventId: Long?,
    )

    data class ReviewSource(
        val usageRecords: List<UsageGuardRecord>,
        val interceptEvents: List<SelfControlAttemptEvent>,
    )

    suspend fun loadUsageRequestOverlay(appId: String): UsageRequestOverlay {
        val data = loadUsageRequestOverlayData(
            appId = appId,
            insightAnchorAt = System.currentTimeMillis(),
        )
        return UsageRequestOverlay(
            latestRequestedAt = data.latestRequestedAt,
            recentCompletedIntervalsMs = SelfControlIntervalPolicy.recentCompletedIntervals(
                intervalsMs = data.samples.mapNotNull { it.gapMs },
            ),
        )
    }

    suspend fun loadUsageRequestOverlayData(
        appId: String,
        insightAnchorAt: Long,
    ): UsageRequestOverlayData {
        val latest = usageRecords.getLatestInsightRow(appId)
        val startAt = SelfControlInsightWindowPolicy.windowStartEpochMs(
            nowEpochMs = insightAnchorAt,
            window = SelfControlInsightWindowPolicy.Window.LAST_30_DAYS,
        )
        val rows = usageRecords.queryInsightRows(appId, startAt, insightAnchorAt)
        val samples = rows.mapNotNull { row ->
            row.requestGapMs?.takeIf { it >= 0L }?.let {
                SelfControlInsightWindowPolicy.IntervalSample(
                    id = row.id,
                    occurredAtEpochMs = row.requestedAt,
                    gapMs = it,
                    requestedDurationMinutes = row.requestedDurationMinutes,
                )
            }
        }
        return UsageRequestOverlayData(
            insightAnchorAt = insightAnchorAt,
            latestRequestedAt = latest?.requestedAt,
            anchorStatus = when {
                latest == null -> UsageGapAnchorStatus.NoPreviousRequest
                latest.lastUsageEndedAt != null -> UsageGapAnchorStatus.Available
                else -> UsageGapAnchorStatus.MissingActualEnd
            },
            previousLastUsageEndedAt = latest?.lastUsageEndedAt,
            samples = samples,
        )
    }

    fun interceptOverlayData(
        insightAnchorAt: Long,
        insight: SelfControlAttempt.RecordedAttemptInsight,
    ): InterceptOverlayData = InterceptOverlayData(
        insightAnchorAt = insightAnchorAt,
        samples = insight.samples,
        currentEventId = insight.currentEventId,
    )

    suspend fun recordIntercept(
        descriptor: AttemptDescriptor,
        occurredAt: Long,
    ): SelfControlAttempt.RecordedAttemptInsight {
        val subjectId = normalizeLabel(descriptor.subjectId, "unknown")
        return attemptEvents.recordEventAndGetInsight(
            SelfControlAttemptEvent(
                eventKey = descriptor.eventKey,
                eventKind = descriptor.eventKind,
                subjectId = subjectId,
                subjectLabel = normalizeLabel(descriptor.subjectLabel, subjectId),
                occurredAt = occurredAt,
                intervalMs = null,
            ),
        )
    }

    fun observeReviewSource(startAt: Long, endAt: Long): Flow<ReviewSource> {
        val usageFlow = usageRecords
            .queryByRequestedAtRange(startAt, endAt)
            .mapLatest { records ->
                records.sortedWith(
                    compareBy<UsageGuardRecord> { it.appId }
                        .thenBy { it.requestedAt }
                        .thenBy { it.id },
                )
            }

        return combine(
            usageFlow,
            attemptEvents.queryByOccurredAtRange(startAt, endAt),
        ) { records, events ->
            ReviewSource(
                usageRecords = records,
                interceptEvents = events,
            )
        }
    }

    companion object {
        fun fromDb(): SelfControlIntervalRepository {
            val usageDao = DbSet.usageGuardRecordDao
            val attemptDao = DbSet.selfControlAttemptDao
            return SelfControlIntervalRepository(
                usageRecords = object : UsageRecordSource {
                    override suspend fun queryRecentRecords(
                        appId: String,
                        limit: Int,
                    ): List<UsageGuardRecord> = usageDao.queryRecentRecords(appId, limit)

                    override fun queryByRequestedAtRange(
                        startAt: Long,
                        endAt: Long,
                    ): Flow<List<UsageGuardRecord>> = usageDao.queryByRequestedAtRange(startAt, endAt)

                    override suspend fun queryInsightRows(
                        appId: String,
                        startAt: Long,
                        endAt: Long,
                    ): List<UsageRequestInsightRow> =
                        usageDao.queryByAppAndRequestedAtRange(appId, startAt, endAt)

                    override suspend fun getLatestInsightRow(appId: String): UsageRequestInsightRow? =
                        usageDao.getLatestInsightRow(appId)
                },
                attemptEvents = object : AttemptEventSource {
                    override suspend fun recordEventAndGetInsight(
                        event: SelfControlAttemptEvent,
                    ): SelfControlAttempt.RecordedAttemptInsight =
                        attemptDao.recordEventAndGetInsight(event)

                    override fun queryByOccurredAtRange(
                        startAt: Long,
                        endAt: Long,
                    ): Flow<List<SelfControlAttemptEvent>> =
                        attemptDao.queryByOccurredAtRange(startAt, endAt)

                    override suspend fun queryByEventKeyAndOccurredAtRange(
                        eventKey: String,
                        startAt: Long,
                        endAt: Long,
                    ): List<SelfControlAttemptEvent> =
                        attemptDao.queryByEventKeyAndOccurredAtRange(eventKey, startAt, endAt)
                },
            )
        }

        fun normalizeLabel(value: String, fallback: String): String {
            val normalized = value.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return fallback
            val codePointCount = normalized.codePointCount(0, normalized.length)
            return if (codePointCount <= 80) {
                normalized
            } else {
                normalized.substring(0, normalized.offsetByCodePoints(0, 80))
            }
        }
    }
}
