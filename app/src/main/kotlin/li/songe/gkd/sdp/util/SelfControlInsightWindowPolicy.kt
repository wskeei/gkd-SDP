package li.songe.gkd.sdp.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rolling windows and chart aggregation shared by every self-control overlay. */
object SelfControlInsightWindowPolicy {
    const val MAX_CHART_POINTS = 30

    enum class Window(
        val label: String,
        val durationMs: Long,
        val bucketMs: Long,
        val maxChartPoints: Int,
    ) {
        LAST_24_HOURS("近 24 小时", 24L * 60L * 60L * 1_000L, 60L * 60L * 1_000L, 24),
        LAST_7_DAYS("近 7 天", 7L * 24L * 60L * 60L * 1_000L, 6L * 60L * 60L * 1_000L, 28),
        LAST_30_DAYS("近 30 天", 30L * 24L * 60L * 60L * 1_000L, 24L * 60L * 60L * 1_000L, 30),
    }

    enum class Metric {
        INTERVAL,
        USAGE_RATIO,
    }

    data class IntervalSample(
        val id: Long,
        val occurredAtEpochMs: Long,
        val gapMs: Long?,
        val requestedDurationMinutes: Int?,
    )

    data class Stats(
        val sampleCount: Int,
        val averageMs: Double?,
        val averageRatio: Double?,
        val medianMs: Double?,
        val medianRatio: Double?,
        val minValue: Double?,
        val maxValue: Double?,
    )

    data class ChartPoint(
        val bucketStartAt: Long,
        val label: String,
        val value: Double,
        val sampleCount: Int,
    )

    data class Series(
        val window: Window,
        val metric: Metric,
        val points: List<ChartPoint>,
        val stats: Stats,
        val rawSampleCount: Int,
    )

    fun windowStartEpochMs(nowEpochMs: Long, window: Window): Long {
        if (nowEpochMs <= 0L) return 0L
        return BigDecimal.valueOf(nowEpochMs)
            .subtract(BigDecimal.valueOf(window.durationMs))
            .max(BigDecimal.ZERO)
            .min(BigDecimal.valueOf(Long.MAX_VALUE))
            .toLong()
    }

    fun samplesInWindow(
        samples: List<IntervalSample>,
        nowEpochMs: Long,
        window: Window,
    ): List<IntervalSample> {
        val start = windowStartEpochMs(nowEpochMs, window)
        return samples
            .asSequence()
            .filter { it.occurredAtEpochMs in start..nowEpochMs }
            .sortedWith(compareBy<IntervalSample> { it.occurredAtEpochMs }.thenBy { it.id })
            .toList()
    }

    fun aggregate(
        samples: List<IntervalSample>,
        nowEpochMs: Long,
        window: Window,
        metric: Metric,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Series {
        val selected = samplesInWindow(samples, nowEpochMs, window)
        val values = selected.mapNotNull { valueFor(it, metric) }
        val stats = Stats(
            sampleCount = values.size,
            averageMs = if (metric == Metric.INTERVAL) average(values) else null,
            averageRatio = if (metric == Metric.USAGE_RATIO) average(values) else null,
            medianMs = if (metric == Metric.INTERVAL) median(values) else null,
            medianRatio = if (metric == Metric.USAGE_RATIO) median(values) else null,
            minValue = values.minOrNull(),
            maxValue = values.maxOrNull(),
        )
        val start = windowStartEpochMs(nowEpochMs, window)
        val points = selected
            .mapNotNull { sample ->
                val value = valueFor(sample, metric) ?: return@mapNotNull null
                val offset = (sample.occurredAtEpochMs - start).coerceAtLeast(0L)
                val bucketIndex = (offset / window.bucketMs).coerceIn(0L, window.maxChartPoints - 1L)
                bucketIndex to (sample to value)
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (bucketIndex, rows) ->
                val bucketStart = start + bucketIndex * window.bucketMs
                ChartPoint(
                    bucketStartAt = bucketStart,
                    label = formatBucketLabel(bucketStart, window, zoneId),
                    value = average(rows.map { it.second }) ?: 0.0,
                    sampleCount = rows.size,
                )
            }
        return Series(
            window = window,
            metric = metric,
            points = points,
            stats = stats,
            rawSampleCount = selected.size,
        )
    }

    private fun valueFor(sample: IntervalSample, metric: Metric): Double? = when (metric) {
        Metric.INTERVAL -> sample.gapMs?.takeIf { it >= 0L }?.toDouble()
        Metric.USAGE_RATIO -> UsageRequestRhythmPolicy.ratio(
            gapMs = sample.gapMs,
            durationMinutes = sample.requestedDurationMinutes ?: 0,
        )
    }

    private fun average(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sum = values.fold(BigDecimal.ZERO) { acc, value ->
            acc.add(BigDecimal.valueOf(value))
        }
        return sum.divide(
            BigDecimal.valueOf(values.size.toLong()),
            12,
            RoundingMode.HALF_UP,
        ).toDouble().takeIf { it.isFinite() }
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val value = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            BigDecimal.valueOf(sorted[middle - 1])
                .add(BigDecimal.valueOf(sorted[middle]))
                .divide(BigDecimal.valueOf(2L), 12, RoundingMode.HALF_UP)
                .toDouble()
        }
        return value.takeIf { it.isFinite() }
    }

    private fun formatBucketLabel(
        bucketStartAt: Long,
        window: Window,
        zoneId: ZoneId,
    ): String {
        val formatter = if (window == Window.LAST_30_DAYS) {
            DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT)
        } else {
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
        }
        return formatter.format(Instant.ofEpochMilli(bucketStartAt).atZone(zoneId))
    }
}
