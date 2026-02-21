package li.songe.gkd.sdp.util

import java.time.Instant
import java.time.ZoneId

object AutoReenablePolicy {
    const val MAX_INTERVAL_MINUTES = 240
    const val CHANGE_COOLDOWN_MS = 72L * 60 * 60 * 1000
    const val MIN_DAILY_DISABLE_LIMIT = 1
    const val MAX_DAILY_DISABLE_LIMIT = 5
    private const val AGGRESSIVE_POLL_DELAY_MS = 15_000L

    fun normalizeIntervalMinutes(value: Int): Int = value.coerceIn(0, MAX_INTERVAL_MINUTES)
    fun normalizeDailyDisableLimit(value: Int): Int = value.coerceIn(MIN_DAILY_DISABLE_LIMIT, MAX_DAILY_DISABLE_LIMIT)

    fun canChangeInterval(lastChangedAt: Long, now: Long): Boolean {
        if (lastChangedAt <= 0L) return true
        return now - lastChangedAt >= CHANGE_COOLDOWN_MS
    }

    fun nextEnforceDelayMs(intervalMinutes: Int): Long {
        val normalized = normalizeIntervalMinutes(intervalMinutes)
        return if (normalized == 0) AGGRESSIVE_POLL_DELAY_MS else normalized * 60_000L
    }

    fun localDayStartEpochMs(now: Long): Long {
        val zoneId = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun shouldResetDailyCounter(dayStartAt: Long, now: Long): Boolean {
        return dayStartAt != localDayStartEpochMs(now)
    }
}
