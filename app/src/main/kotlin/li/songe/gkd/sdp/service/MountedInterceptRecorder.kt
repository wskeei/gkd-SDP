package li.songe.gkd.sdp.service

import java.util.LinkedHashMap
import li.songe.gkd.sdp.data.RuleTriggerLogRepository
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy

/**
 * Persists an accepted interception only after the overlay has actually mounted.
 *
 * The two sinks intentionally have independent success boundaries: an ActionLog write failure
 * must not prevent the interval insight event, and vice versa. This class accepts only already
 * sanitized descriptors/snapshots, never raw selector/URL/message data.
 */
class MountedInterceptRecorder(
    private val actionLogSink: ActionLogSink,
    private val attemptSink: AttemptSink,
) {
    interface ActionLogSink {
        suspend fun recordIntercepted(snapshot: SelectorRuleSnapshot): Long
    }

    interface AttemptSink {
        suspend fun recordIntercept(
            descriptor: SelfControlIntervalRepository.AttemptDescriptor,
            occurredAt: Long,
        ): SelfControlAttempt.RecordedAttemptInsight?
    }

    data class Pending(
        val recordToken: String,
        val eventKey: String,
        val eventKind: Int,
        val subjectId: String,
        val subjectLabel: String,
        val selectorSnapshot: SelectorRuleSnapshot? = null,
    ) {
        fun isValid(): Boolean {
            if (recordToken.isBlank() || eventKey.isBlank() || subjectId.isBlank()) return false
            return when (eventKind) {
                SelfControlAttempt.KIND_SELECTOR_INTERCEPT ->
                    selectorSnapshot != null && selectorSnapshot.eventKey() == eventKey

                SelfControlAttempt.KIND_URL_INTERCEPT ->
                    selectorSnapshot == null && subjectId.toLongOrNull()?.let { ruleId ->
                        eventKey == SelfControlElapsedPolicy.urlInterceptEventKey(ruleId)
                    } == true
                else -> false
            }
        }
    }

    data class Result(
        val actionLogAttempted: Boolean,
        val actionLogSucceeded: Boolean,
        val intervalAttempted: Boolean,
        val intervalSucceeded: Boolean,
        val intervalInsight: SelfControlAttempt.RecordedAttemptInsight? = null,
    )

    private val tokenLock = Any()
    private val completedTokens = object : LinkedHashMap<String, Unit>(TOKEN_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean =
            size > TOKEN_LIMIT
    }

    suspend fun recordMounted(
        pending: Pending,
        mounted: Boolean,
        occurredAt: Long,
    ): Result {
        if (!mounted || !pending.isValid()) return Result(false, false, false, false)
        val isNewToken = synchronized(tokenLock) {
            completedTokens.put(pending.recordToken, Unit) == null
        }
        if (!isNewToken) return Result(false, false, false, false)

        var actionAttempted = false
        var actionSucceeded = false
        if (pending.eventKind == SelfControlAttempt.KIND_SELECTOR_INTERCEPT) {
            actionAttempted = true
            actionSucceeded = runCatching {
                actionLogSink.recordIntercepted(requireNotNull(pending.selectorSnapshot))
            }.isSuccess
        }

        val intervalAttempted = true
        val intervalResult = runCatching {
            attemptSink.recordIntercept(
                descriptor = SelfControlIntervalRepository.AttemptDescriptor(
                    eventKey = pending.eventKey,
                    eventKind = pending.eventKind,
                    subjectId = pending.subjectId,
                    subjectLabel = pending.subjectLabel,
                ),
                occurredAt = occurredAt,
            )
        }

        return Result(
            actionLogAttempted = actionAttempted,
            actionLogSucceeded = actionSucceeded,
            intervalAttempted = intervalAttempted,
            intervalSucceeded = intervalResult.isSuccess,
            intervalInsight = intervalResult.getOrNull(),
        )
    }

    companion object {
        private const val TOKEN_LIMIT = 1_024

        fun fromDb(): MountedInterceptRecorder {
            val logRepository = RuleTriggerLogRepository.fromDb()
            val intervalRepository = SelfControlIntervalRepository.fromDb()
            return MountedInterceptRecorder(
                actionLogSink = object : ActionLogSink {
                    override suspend fun recordIntercepted(snapshot: SelectorRuleSnapshot): Long =
                        logRepository.recordIntercepted(snapshot)
                },
                attemptSink = object : AttemptSink {
                    override suspend fun recordIntercept(
                        descriptor: SelfControlIntervalRepository.AttemptDescriptor,
                        occurredAt: Long,
                    ): SelfControlAttempt.RecordedAttemptInsight? =
                        intervalRepository.recordIntercept(descriptor, occurredAt)
                },
            )
        }
    }
}
