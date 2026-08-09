package li.songe.gkd.sdp.remote

import kotlin.math.ceil

enum class RemoteRequestKind {
    DEFAULT,
    CAPTURE,
    EXEC,
}

enum class RemoteLimitError {
    RATE_LIMITED,
    REQUEST_TOO_LARGE,
    RESPONSE_TOO_LARGE,
}

data class RemoteLimitResult(
    val allowed: Boolean,
    val error: RemoteLimitError? = null,
    val retryAfterSeconds: Int = 0,
)

class RemoteRateLimiter {
    companion object {
        const val MAX_REQUESTS_PER_MINUTE = 60
        const val MAX_CAPTURES_PER_MINUTE = 6
        const val MAX_EXECUTIONS_PER_MINUTE = 10
        const val MAX_REQUEST_BYTES = 1L * 1024L * 1024L
        const val MAX_RESPONSE_BYTES = 32L * 1024L * 1024L
        private const val WINDOW_MILLIS = 60_000L
    }

    private val totalRequests = mutableMapOf<String, ArrayDeque<Long>>()
    private val specializedRequests = mutableMapOf<Pair<String, RemoteRequestKind>, ArrayDeque<Long>>()

    @Synchronized
    fun check(key: String, kind: RemoteRequestKind, nowMillis: Long): RemoteLimitResult {
        val total = totalRequests.getOrPut(key) { ArrayDeque() }
        prune(total, nowMillis)
        val specificLimit = when (kind) {
            RemoteRequestKind.DEFAULT -> null
            RemoteRequestKind.CAPTURE -> MAX_CAPTURES_PER_MINUTE
            RemoteRequestKind.EXEC -> MAX_EXECUTIONS_PER_MINUTE
        }
        val specific = specificLimit?.let {
            specializedRequests.getOrPut(key to kind) { ArrayDeque() }.also { values ->
                prune(values, nowMillis)
            }
        }
        val limitingQueue = when {
            total.size >= MAX_REQUESTS_PER_MINUTE -> total
            specificLimit != null && requireNotNull(specific).size >= specificLimit -> specific
            else -> null
        }
        if (limitingQueue != null) {
            val remainingMillis = WINDOW_MILLIS - (nowMillis - limitingQueue.first())
            return RemoteLimitResult(
                allowed = false,
                error = RemoteLimitError.RATE_LIMITED,
                retryAfterSeconds = ceil(remainingMillis.coerceAtLeast(1) / 1_000.0).toInt(),
            )
        }
        total += nowMillis
        specific?.addLast(nowMillis)
        return RemoteLimitResult(allowed = true)
    }

    fun validateRequestBytes(bytes: Long): RemoteLimitResult = if (bytes in 0..MAX_REQUEST_BYTES) {
        RemoteLimitResult(allowed = true)
    } else {
        RemoteLimitResult(false, RemoteLimitError.REQUEST_TOO_LARGE)
    }

    fun validateResponseBytes(bytes: Long): RemoteLimitResult = if (bytes in 0..MAX_RESPONSE_BYTES) {
        RemoteLimitResult(allowed = true)
    } else {
        RemoteLimitResult(false, RemoteLimitError.RESPONSE_TOO_LARGE)
    }

    @Synchronized
    fun clear(key: String? = null) {
        if (key == null) {
            totalRequests.clear()
            specializedRequests.clear()
        } else {
            totalRequests.remove(key)
            specializedRequests.keys.removeAll { it.first == key }
        }
    }

    private fun prune(values: ArrayDeque<Long>, nowMillis: Long) {
        while (values.isNotEmpty() && nowMillis - values.first() >= WINDOW_MILLIS) {
            values.removeFirst()
        }
    }
}
