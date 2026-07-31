package li.songe.gkd.sdp.store

import kotlinx.serialization.Serializable

@Serializable
data class AccessibilityGuardSession(
    val generation: Long = 0L,
    val disabledAtEpochMs: Long = 0L,
    val lastReminderIndex: Int = -1,
    val enforcementStarted: Boolean = false,
    val temporaryShutdownExpected: Boolean = false,
    val grantFlowUntilEpochMs: Long = 0L,
)

val accessibilityGuardSessionFlow by lazy {
    createAnyFlow(
        key = "accessibility_guard_session",
        default = { AccessibilityGuardSession() },
        private = true,
    )
}
