package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.R

/**
 * Pure copy and schedule rules for accessibility-permission reminders.
 *
 * Keeping this separate from Android notification construction makes the
 * elapsed-time labels and the final warning copy straightforward to verify
 * from the JVM test source set.
 */
object AccessibilityGuardNotificationPolicy {
    data class GuardStatusNotification(
        val textRes: Int,
        val textArgs: List<Int> = emptyList(),
        val targetEpochMs: Long?,
        val nextReminderIndex: Int?,
    )

    /** Cumulative elapsed minutes represented by the six reminder notices. */
    val ELAPSED_MINUTES: IntArray = AccessibilityGuardPolicy.REMINDER_OFFSETS_MS
        .map { (it / AccessibilityGuardPolicy.MINUTE_MS).toInt() }
        .toIntArray()

    const val TITLE_RES = R.string.a11y_guard_title

    fun isValidIndex(index: Int): Boolean = index in ELAPSED_MINUTES.indices

    fun elapsedMinutes(index: Int): Int {
        require(isValidIndex(index)) { "Invalid accessibility guard reminder index: $index" }
        return ELAPSED_MINUTES[index]
    }

    fun textRes(index: Int): Int =
        if (index == ELAPSED_MINUTES.lastIndex) {
            R.string.a11y_guard_final_reminder
        } else {
            R.string.a11y_guard_reminder
        }

    fun textArgs(index: Int): List<Int> = listOf(elapsedMinutes(index))

    /**
     * Builds the stable notification shown between stage reminders.
     *
     * The target is always derived from the canonical cumulative offsets so a
     * process restart or a late reconciliation cannot drift the countdown.
     * A malformed/empty persisted session produces no notification model.
     */
    fun status(
        disabledAtEpochMs: Long,
        lastReminderIndex: Int,
        enforcementStarted: Boolean,
    ): GuardStatusNotification? {
        if (disabledAtEpochMs <= 0L ||
            lastReminderIndex !in -1..ELAPSED_MINUTES.lastIndex
        ) {
            return null
        }
        if (enforcementStarted) {
            if (lastReminderIndex != ELAPSED_MINUTES.lastIndex) return null
            return GuardStatusNotification(
                textRes = R.string.a11y_guard_last_sent,
                targetEpochMs = null,
                nextReminderIndex = null,
            )
        }

        if (lastReminderIndex == ELAPSED_MINUTES.lastIndex) return null
        val nextReminderIndex = lastReminderIndex + 1
        val targetEpochMs = disabledAtEpochMs +
            AccessibilityGuardPolicy.REMINDER_OFFSETS_MS[nextReminderIndex]
        return GuardStatusNotification(
            textRes = R.string.a11y_guard_next_reminder,
            textArgs = listOf(nextReminderIndex + 1),
            targetEpochMs = targetEpochMs,
            nextReminderIndex = nextReminderIndex,
        )
    }
}
