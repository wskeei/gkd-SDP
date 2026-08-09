package li.songe.gkd.sdp.diagnostics

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticSeverity {
    DEBUG,
    WARNING,
    ERROR,
}

@Serializable
enum class DiagnosticEventCode(val severity: DiagnosticSeverity) {
    LEGACY_CALL(DiagnosticSeverity.DEBUG),
    LEGACY_FAILURE(DiagnosticSeverity.WARNING),
    COROUTINE_FAILURE(DiagnosticSeverity.WARNING),
    CRASH_CAPTURED(DiagnosticSeverity.ERROR),
    RUNTIME_FAILURE(DiagnosticSeverity.WARNING),
    REMOTE_REQUEST_REJECTED(DiagnosticSeverity.WARNING),
    FILE_OPERATION_FAILURE(DiagnosticSeverity.WARNING),
}

@Serializable
enum class DiagnosticStage {
    LEGACY,
    APP,
    ACCESSIBILITY,
    AUTOMATION,
    SERVICE,
    OVERLAY,
    DATABASE,
    STORAGE,
    NETWORK,
    USER_INTERFACE,
}

@Serializable
enum class DiagnosticResult {
    OBSERVED,
    ACCEPTED,
    REJECTED,
    FAILED,
}

@Serializable
enum class DiagnosticDurationBucket {
    UNDER_100_MS,
    UNDER_1_SECOND,
    UNDER_10_SECONDS,
    UNDER_1_MINUTE,
    AT_LEAST_1_MINUTE,
}

@Serializable
enum class DiagnosticErrorCategory {
    IO,
    NETWORK,
    DATABASE,
    PERMISSION,
    SECURITY,
    INVALID_INPUT,
    INVALID_STATE,
    RESOURCE_EXHAUSTED,
    UNKNOWN,
}

@Serializable
data class DiagnosticEvent(
    val eventCode: DiagnosticEventCode,
    val stage: DiagnosticStage? = null,
    val result: DiagnosticResult? = null,
    val entityHash: String? = null,
    val count: Int? = null,
    val durationBucket: DiagnosticDurationBucket? = null,
    val errorCategory: DiagnosticErrorCategory? = null,
)

class DiagnosticRateLimiter(
    private val maxEvents: Int,
    private val windowMillis: Long,
) {
    private val timestampsByEvent = mutableMapOf<DiagnosticEvent, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(event: DiagnosticEvent, nowMillis: Long): Boolean {
        val timestamps = timestampsByEvent.getOrPut(event) { ArrayDeque() }
        val cutoff = nowMillis - windowMillis
        while (timestamps.firstOrNull()?.let { it <= cutoff } == true) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxEvents) return false
        timestamps.addLast(nowMillis)
        return true
    }
}
