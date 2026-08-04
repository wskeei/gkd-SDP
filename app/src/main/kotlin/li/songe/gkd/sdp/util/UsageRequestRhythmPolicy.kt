package li.songe.gkd.sdp.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Pure, clock-free semantics for usage-request rhythm and 间用比. */
object UsageRequestRhythmPolicy {
    private const val MINUTE_MS = 60_000L
    private const val RATIO_SCALE = 12

    data class Sample(
        val gapMs: Long?,
        val durationMinutes: Int,
    )

    fun gapMs(lastUsageEndedAt: Long?, requestedAt: Long): Long? {
        if (lastUsageEndedAt == null || lastUsageEndedAt < 0L || requestedAt < 0L) return null
        if (requestedAt < lastUsageEndedAt) return null
        return BigDecimal.valueOf(requestedAt)
            .subtract(BigDecimal.valueOf(lastUsageEndedAt))
            .min(BigDecimal.valueOf(Long.MAX_VALUE))
            .toLong()
    }

    fun ratio(gapMs: Long?, durationMinutes: Int): Double? {
        if (gapMs == null || gapMs < 0L || durationMinutes <= 0) return null
        val durationMs = BigDecimal.valueOf(durationMinutes.toLong()).multiply(
            BigDecimal.valueOf(MINUTE_MS),
        )
        val value = BigDecimal.valueOf(gapMs)
            .divide(durationMs, RATIO_SCALE, RoundingMode.HALF_UP)
            .toDouble()
        return value.takeIf { it.isFinite() }
    }

    fun currentRatio(gapMs: Long?, durationMinutes: Int): Double? =
        ratio(gapMs, durationMinutes)

    fun averageRatio(samples: Iterable<Sample>): Double? {
        val ratios = samples.mapNotNull { ratio(it.gapMs, it.durationMinutes) }
        if (ratios.isEmpty()) return null
        val sum = ratios.fold(BigDecimal.ZERO) { acc, value ->
            acc.add(BigDecimal.valueOf(value))
        }
        return sum.divide(BigDecimal.valueOf(ratios.size.toLong()), RATIO_SCALE, RoundingMode.HALF_UP)
            .toDouble()
            .takeIf { it.isFinite() }
    }

    fun formatRatio(value: Double?): String? {
        if (value == null || !value.isFinite() || value < 0.0) return "暂无"
        return "%.2f".format(Locale.ROOT, value)
    }
}
