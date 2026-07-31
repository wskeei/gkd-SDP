package li.songe.gkd.sdp.util

/**
 * Pure timing and visibility rules for the accessibility-permission guard.
 *
 * The reminder schedule is cumulative: after the permission is observed off,
 * reminders are due at 15, 25, 30, 33, 35 and 36 minutes. Keeping this policy
 * free of Android types makes it safe to exercise from the JVM test source set
 * and lets the coordinator reconcile state after a process restart.
 */
object AccessibilityGuardPolicy {
    const val MINUTE_MS = 60_000L

    /** Delay between each reminder and the previous reminder. */
    val REMINDER_INTERVALS_MINUTES = intArrayOf(15, 10, 5, 3, 2, 1)

    /** Absolute offsets from the time accessibility was observed disabled. */
    val REMINDER_OFFSETS_MS = longArrayOf(15L, 25L, 30L, 33L, 35L, 36L)
        .map { it * MINUTE_MS }
        .toLongArray()

    enum class SessionMode {
        /** The feature is disabled or the app is not operating in A11y mode. */
        DISABLED,

        /** A local/temporary shutdown while a blocked app is in the foreground. */
        TEMPORARY_SHUTDOWN,

        /** A user/manual permission loss that should start the guard session. */
        MANUAL_DISABLED,
    }

    data class Evaluation(
        /** The highest newly due reminder index, or null when no reminder is due. */
        val reminderIndex: Int?,
        /** Whether the final checkpoint should begin enforcement now. */
        val enforce: Boolean,
        /** Absolute time at which the next reminder becomes due, if any. */
        val nextWakeAt: Long?,
    ) {
        // Descriptive aliases keep callers readable without duplicating policy state.
        val shouldEnforce: Boolean get() = enforce
        val nextReminderAt: Long? get() = nextWakeAt
    }

    /**
     * Classifies the reason a disabled accessibility service should (or should
     * not) create a guard session.
     */
    fun sessionMode(
        featureEnabled: Boolean,
        useA11yMode: Boolean,
        temporaryShutdown: Boolean,
        currentAppBlocked: Boolean,
    ): SessionMode {
        if (!featureEnabled || !useA11yMode) return SessionMode.DISABLED
        if (temporaryShutdown && currentAppBlocked) return SessionMode.TEMPORARY_SHUTDOWN
        return SessionMode.MANUAL_DISABLED
    }

    /**
     * Returns the actions due at [nowEpochMs] for a persisted disabled session.
     *
     * Absolute offsets are used instead of chaining delays, so a process that
     * wakes late emits only the latest due reminder and does not replay every
     * missed notification. A zero disabled timestamp represents a reset
     * session and therefore has no actions.
     */
    fun evaluate(
        disabledAtEpochMs: Long,
        lastReminderIndex: Int,
        enforcementStarted: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Evaluation {
        if (disabledAtEpochMs <= 0L) {
            return Evaluation(
                reminderIndex = null,
                enforce = false,
                nextWakeAt = null,
            )
        }

        val elapsedMs = (nowEpochMs - disabledAtEpochMs).coerceAtLeast(0L)
        val highestDueIndex = REMINDER_OFFSETS_MS.indexOfLast { elapsedMs >= it }
        val reminderIndex = highestDueIndex
            .takeIf { it >= 0 && it > lastReminderIndex }
        val enforce = !enforcementStarted &&
            highestDueIndex == REMINDER_OFFSETS_MS.lastIndex
        val nextWakeAt = if (enforcementStarted) {
            null
        } else {
            REMINDER_OFFSETS_MS
                .firstOrNull { elapsedMs < it }
                ?.let { disabledAtEpochMs + it }
        }

        return Evaluation(
            reminderIndex = reminderIndex,
            enforce = enforce,
            nextWakeAt = nextWakeAt,
        )
    }

    /** Convenience predicate used by the coordinator when beginning a session. */
    fun shouldGuard(
        featureEnabled: Boolean,
        useA11yMode: Boolean,
        temporaryShutdown: Boolean,
        currentAppBlocked: Boolean,
    ): Boolean {
        return sessionMode(
            featureEnabled = featureEnabled,
            useA11yMode = useA11yMode,
            temporaryShutdown = temporaryShutdown,
            currentAppBlocked = currentAppBlocked,
        ) == SessionMode.MANUAL_DISABLED
    }

    data class OverlayInput(
        val sessionMode: SessionMode = SessionMode.DISABLED,
        val enforcementStarted: Boolean = false,
        val accessibilityEnabled: Boolean = false,
        val appVisible: Boolean = false,
        val grantFlowActive: Boolean = false,
        val overlayPermissionGranted: Boolean = false,
        val screenInteractive: Boolean = true,
        /** A short debounce/suppression window after the CTA opens the app. */
        val suppressedUntilEpochMs: Long = 0L,
        val nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val a11yEnabled: Boolean get() = accessibilityEnabled
    }

    /**
     * Determines whether the full-screen overlay may be displayed right now.
     * The system settings grant flow, visible app UI, non-interactive screen,
     * missing overlay permission and temporary shutdowns all suppress it.
     */
    fun shouldShowOverlay(input: OverlayInput): Boolean {
        if (input.sessionMode != SessionMode.MANUAL_DISABLED) return false
        if (!input.enforcementStarted || input.accessibilityEnabled) return false
        if (input.appVisible || input.grantFlowActive) return false
        if (!input.overlayPermissionGranted || !input.screenInteractive) return false
        if (input.nowEpochMs < input.suppressedUntilEpochMs) return false
        return true
    }
}
