package li.songe.gkd.sdp.performance

enum class DebugRuntimeViolation {
    MAIN_THREAD_DISK,
    MAIN_THREAD_NETWORK,
    LEAKED_CLOSABLE,
    ACTIVITY_LEAK,
    CLEARTEXT_NETWORK,
}

data class DebugRuntimeEvent(
    val violation: DebugRuntimeViolation,
    val detail: String,
)

fun interface DebugRuntimeViolationListener {
    fun onViolation(event: DebugRuntimeEvent)
}
