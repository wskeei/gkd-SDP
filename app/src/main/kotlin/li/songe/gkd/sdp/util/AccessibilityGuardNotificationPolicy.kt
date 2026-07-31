package li.songe.gkd.sdp.util

/**
 * Pure copy and schedule rules for accessibility-permission reminders.
 *
 * Keeping this separate from Android notification construction makes the
 * elapsed-time labels and the final warning copy straightforward to verify
 * from the JVM test source set.
 */
object AccessibilityGuardNotificationPolicy {
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
}
