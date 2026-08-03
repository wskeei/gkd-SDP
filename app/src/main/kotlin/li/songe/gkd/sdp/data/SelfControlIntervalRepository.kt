package li.songe.gkd.sdp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import li.songe.gkd.sdp.db.DbSet
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

        suspend fun getPreviousRecord(appId: String, requestedAt: Long, id: Long): UsageGuardRecord?

        fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>>
    }

    interface AttemptEventSource {
        suspend fun recordEventAndGetInsight(
            event: SelfControlAttemptEvent,
        ): SelfControlAttempt.RecordedAttemptInsight

        fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>>
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

    data class ReviewSource(
        val usageRecords: List<UsageGuardRecord>,
        val interceptEvents: List<SelfControlAttemptEvent>,
    )

    suspend fun loadUsageRequestOverlay(appId: String): UsageRequestOverlay {
        val records = usageRecords.queryRecentRecords(
            appId = appId,
            limit = SelfControlIntervalPolicy.DEFAULT_OVERLAY_HISTORY_LIMIT + 1,
        )
        val events = records.map {
            SelfControlIntervalPolicy.Event(
                key = it.appId,
                occurredAtEpochMs = it.requestedAt,
                id = it.id,
            )
        }
        return UsageRequestOverlay(
            latestRequestedAt = records.maxWithOrNull(
                compareBy<UsageGuardRecord> { it.requestedAt }.thenBy { it.id },
            )?.requestedAt,
            recentCompletedIntervalsMs = SelfControlIntervalPolicy.recentCompletedIntervals(
                intervalsMs = SelfControlIntervalPolicy.intervalsForKey(events, appId),
            ),
        )
    }

    suspend fun recordIntercept(
        descriptor: AttemptDescriptor,
        occurredAt: Long,
    ): SelfControlAttempt.RecordedAttemptInsight {
        return attemptEvents.recordEventAndGetInsight(
            SelfControlAttemptEvent(
                eventKey = descriptor.eventKey,
                eventKind = descriptor.eventKind,
                subjectId = normalizeLabel(descriptor.subjectId, "unknown"),
                subjectLabel = normalizeLabel(descriptor.subjectLabel, descriptor.subjectId),
                occurredAt = occurredAt,
                intervalMs = null,
            ),
        )
    }

    fun observeReviewSource(startAt: Long, endAt: Long): Flow<ReviewSource> {
        val usageFlow = usageRecords
            .queryByRequestedAtRange(startAt, endAt)
            .mapLatest { records ->
                val firstRecordByApp = records
                    .groupBy { it.appId }
                    .values
                    .mapNotNull { appRecords ->
                        appRecords.minWithOrNull(
                            compareBy<UsageGuardRecord> { it.requestedAt }.thenBy { it.id },
                        )
                    }
                val predecessors = firstRecordByApp.mapNotNull { first ->
                    usageRecords.getPreviousRecord(
                        appId = first.appId,
                        requestedAt = first.requestedAt,
                        id = first.id,
                    )
                }
                (predecessors + records)
                    .distinctBy { it.id }
                    .sortedWith(compareBy<UsageGuardRecord> { it.appId }.thenBy { it.requestedAt }.thenBy { it.id })
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

                    override suspend fun getPreviousRecord(
                        appId: String,
                        requestedAt: Long,
                        id: Long,
                    ): UsageGuardRecord? = usageDao.getPreviousRecord(appId, requestedAt, id)

                    override fun queryByRequestedAtRange(
                        startAt: Long,
                        endAt: Long,
                    ): Flow<List<UsageGuardRecord>> = usageDao.queryByRequestedAtRange(startAt, endAt)
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
