package li.songe.gkd.sdp.util

object AutoReenablePolicy {
    const val MAX_INTERVAL_MINUTES = 240
    const val CHANGE_COOLDOWN_MS = 72L * 60 * 60 * 1000
    private const val AGGRESSIVE_POLL_DELAY_MS = 15_000L

    fun normalizeIntervalMinutes(value: Int): Int = value.coerceIn(0, MAX_INTERVAL_MINUTES)

    fun canChangeInterval(lastChangedAt: Long, now: Long): Boolean {
        if (lastChangedAt <= 0L) return true
        return now - lastChangedAt >= CHANGE_COOLDOWN_MS
    }

    fun nextEnforceDelayMs(intervalMinutes: Int): Long {
        val normalized = normalizeIntervalMinutes(intervalMinutes)
        return if (normalized == 0) AGGRESSIVE_POLL_DELAY_MS else normalized * 60_000L
    }
}
