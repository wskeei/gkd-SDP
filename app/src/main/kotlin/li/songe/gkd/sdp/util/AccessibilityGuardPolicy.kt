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
        /** No tracking session is needed or the permission has recovered. */
        RESET,

        /** A local/temporary shutdown while a blocked app is in the foreground. */
        SUPPRESSED_TEMPORARY,

        /** A user/manual permission loss that should start the guard session. */
        TRACK,
    }

    data class Evaluation(
        /** The highest newly due reminder index, or null when no reminder is due. */
        val dueReminderIndex: Int?,
        /** Whether the final checkpoint should begin enforcement now. */
        val startEnforcement: Boolean,
        /** Absolute time at which the next reminder becomes due, if any. */
        val nextWakeAtEpochMs: Long?,
    ) {
        // Backward-compatible aliases for callers that adopted the initial draft API.
        val reminderIndex: Int? get() = dueReminderIndex
        val enforce: Boolean get() = startEnforcement
        val nextWakeAt: Long? get() = nextWakeAtEpochMs
        val shouldEnforce: Boolean get() = startEnforcement
        val nextReminderAt: Long? get() = nextWakeAtEpochMs
    }

    /**
     * Classifies the reason a disabled accessibility service should (or should
     * not) create a guard session.
     */
    fun sessionMode(
        featureEnabled: Boolean,
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        a11yEnabled: Boolean,
        temporaryShutdownExpected: Boolean,
        currentAppBlocked: Boolean,
    ): SessionMode {
        // A recovered permission ends the current guard session. The normal
        // guard trigger is therefore represented by a11yEnabled == false.
        if (!featureEnabled || !useA11yMode || !strictChannelAvailable || a11yEnabled) {
            return SessionMode.RESET
        }
        if (temporaryShutdownExpected && currentAppBlocked) return SessionMode.SUPPRESSED_TEMPORARY
        return SessionMode.TRACK
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
        nowEpochMs: Long,
    ): Evaluation {
        if (disabledAtEpochMs <= 0L) {
            return Evaluation(
                dueReminderIndex = null,
                startEnforcement = false,
                nextWakeAtEpochMs = null,
            )
        }

        val elapsedMs = (nowEpochMs - disabledAtEpochMs).coerceAtLeast(0L)
        val highestDueIndex = REMINDER_OFFSETS_MS.indexOfLast { elapsedMs >= it }
        val dueReminderIndex = highestDueIndex
            .takeIf { it >= 0 && it > lastReminderIndex }
        val startEnforcement = !enforcementStarted &&
            highestDueIndex == REMINDER_OFFSETS_MS.lastIndex
        val nextWakeAtEpochMs = if (enforcementStarted) {
            null
        } else {
            REMINDER_OFFSETS_MS
                .firstOrNull { elapsedMs < it }
                ?.let { disabledAtEpochMs + it }
        }

        return Evaluation(
            dueReminderIndex = dueReminderIndex,
            startEnforcement = startEnforcement,
            nextWakeAtEpochMs = nextWakeAtEpochMs,
        )
    }

    /** Convenience predicate used by the coordinator when beginning a session. */
    fun shouldGuard(
        featureEnabled: Boolean,
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        a11yEnabled: Boolean,
        temporaryShutdownExpected: Boolean,
        currentAppBlocked: Boolean,
    ): Boolean {
        return sessionMode(
            featureEnabled = featureEnabled,
            strictChannelAvailable = strictChannelAvailable,
            useA11yMode = useA11yMode,
            a11yEnabled = a11yEnabled,
            temporaryShutdownExpected = temporaryShutdownExpected,
            currentAppBlocked = currentAppBlocked,
        ) == SessionMode.TRACK
    }

    data class OverlayInput(
        val enforcementStarted: Boolean,
        val a11yEnabled: Boolean,
        val appVisible: Boolean,
        /** Grant/settings flow suppression ends at this absolute timestamp. */
        val grantFlowUntilEpochMs: Long,
        val nowEpochMs: Long,
        val canDrawOverlays: Boolean,
        val screenInteractive: Boolean,
        val keyguardLocked: Boolean,
    ) {
        // Aliases for callers that adopted the initial draft API.
        val accessibilityEnabled: Boolean get() = a11yEnabled
        val grantFlowActive: Boolean get() = nowEpochMs < grantFlowUntilEpochMs
        val overlayPermissionGranted: Boolean get() = canDrawOverlays
        val suppressedUntilEpochMs: Long get() = grantFlowUntilEpochMs
    }

    /**
     * Determines whether the full-screen overlay may be displayed right now.
     * The system settings grant flow, visible app UI, non-interactive screen,
     * missing overlay permission and a locked keyguard all suppress it.
     */
    fun shouldShowOverlay(input: OverlayInput): Boolean {
        if (!input.enforcementStarted || input.a11yEnabled) return false
        if (input.appVisible) return false
        if (input.nowEpochMs < input.grantFlowUntilEpochMs) return false
        if (!input.canDrawOverlays || !input.screenInteractive || input.keyguardLocked) return false
        return true
    }
}
