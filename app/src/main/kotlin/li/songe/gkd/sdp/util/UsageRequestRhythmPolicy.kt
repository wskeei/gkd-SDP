package li.songe.gkd.sdp.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure, clock-free semantics for usage-request rhythm and 间用比. */
object UsageRequestRhythmPolicy {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS
    private const val RATIO_SCALE = 12

    data class Sample(
        val gapMs: Long?,
        val durationMinutes: Int,
    )

    enum class FormulaUnit(
        val divisorMs: Long,
        val label: String,
    ) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        SECONDS(1_000L, "秒"),
        // i18n-ignore: legacy fallback or non-display heuristic data
        MINUTES(MINUTE_MS, "分钟"),
        // i18n-ignore: legacy fallback or non-display heuristic data
        HOURS(HOUR_MS, "小时"),
    }

    data class Formula(
        val gapValue: BigDecimal,
        val durationValue: BigDecimal,
        val unit: FormulaUnit,
        val ratio: Double,
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

    fun formula(gapMs: Long?, durationMinutes: Int): Formula? {
        if (gapMs == null || gapMs < 0L || durationMinutes <= 0) return null
        val durationMs = BigDecimal.valueOf(durationMinutes.toLong()).multiply(
            BigDecimal.valueOf(MINUTE_MS),
        )
        val unit = when {
            gapMs >= HOUR_MS && durationMs >= BigDecimal.valueOf(HOUR_MS) -> FormulaUnit.HOURS
            gapMs >= MINUTE_MS && durationMs >= BigDecimal.valueOf(MINUTE_MS) -> FormulaUnit.MINUTES
            else -> FormulaUnit.SECONDS
        }
        val divisor = BigDecimal.valueOf(unit.divisorMs)
        return Formula(
            gapValue = BigDecimal.valueOf(gapMs).divide(divisor, RATIO_SCALE, RoundingMode.HALF_UP),
            durationValue = durationMs.divide(divisor, RATIO_SCALE, RoundingMode.HALF_UP),
            unit = unit,
            ratio = ratio(gapMs, durationMinutes) ?: return null,
        )
    }

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
        if (value == null || !value.isFinite() || value < 0.0) return null
        if (value > 0.0 && value < 0.01) return "<0.01"
        val scale = when {
            value >= 100.0 -> 0
            value >= 10.0 -> 1
            else -> 2
        }
        var formatted = BigDecimal.valueOf(value)
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        if (value < 100.0 && !formatted.contains('.')) {
            formatted += ".0"
        }
        return formatted
    }
}
