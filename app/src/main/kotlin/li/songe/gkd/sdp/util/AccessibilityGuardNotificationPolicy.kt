package li.songe.gkd.sdp.util

/**
 * Pure copy and schedule rules for accessibility-permission reminders.
 *
 * Keeping this separate from Android notification construction makes the
 * elapsed-time labels and the final warning copy straightforward to verify
 * from the JVM test source set.
 */
object AccessibilityGuardNotificationPolicy {
    data class GuardStatusNotification(
        val text: String,
        val targetEpochMs: Long?,
        val nextReminderIndex: Int?,
    )

    /** Cumulative elapsed minutes represented by the six reminder notices. */
    val ELAPSED_MINUTES: IntArray = AccessibilityGuardPolicy.REMINDER_OFFSETS_MS
        .map { (it / AccessibilityGuardPolicy.MINUTE_MS).toInt() }
        .toIntArray()

    const val TITLE = "无障碍权限已关闭"

    fun isValidIndex(index: Int): Boolean = index in ELAPSED_MINUTES.indices

    fun elapsedMinutes(index: Int): Int {
        require(isValidIndex(index)) { "Invalid accessibility guard reminder index: $index" }
        return ELAPSED_MINUTES[index]
    }

    fun text(index: Int): String {
        val elapsedMinutes = elapsedMinutes(index)
        return if (index == ELAPSED_MINUTES.lastIndex) {
            "已关闭 $elapsedMinutes 分钟，将显示全屏提醒，请前往重新开启"
        } else {
            "已关闭 $elapsedMinutes 分钟，请前往重新开启"
        }
    }

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
                text = "最后提醒已发送，请立即开启无障碍",
                targetEpochMs = null,
                nextReminderIndex = null,
            )
        }

        if (lastReminderIndex == ELAPSED_MINUTES.lastIndex) return null
        val nextReminderIndex = lastReminderIndex + 1
        val targetEpochMs = disabledAtEpochMs +
            AccessibilityGuardPolicy.REMINDER_OFFSETS_MS[nextReminderIndex]
        return GuardStatusNotification(
            text = "距离第 ${nextReminderIndex + 1} 次提醒",
            targetEpochMs = targetEpochMs,
            nextReminderIndex = nextReminderIndex,
        )
    }
}
