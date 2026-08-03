package li.songe.gkd.sdp.util

import java.math.BigInteger
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Pure calculations shared by the usage-request and self-control surfaces.
 *
 * The policy deliberately does not know about Room, Android clocks, or Compose. This keeps
 * interval semantics deterministic and makes it impossible for a one-second UI ticker to
 * accidentally query the database.
 */
object SelfControlIntervalPolicy {
    const val DEFAULT_OVERLAY_HISTORY_LIMIT = 5

    enum class SampleQuality {
        NoSample,
        Limited,
        Ready,
    }

    enum class AxisUnit(val divisorMs: Long, val suffix: String) {
        Seconds(1_000L, "秒"),
        Minutes(60_000L, "分"),
        Hours(3_600_000L, "小时"),
        Days(86_400_000L, "天"),
    }

    data class Event(
        val key: String,
        val occurredAtEpochMs: Long,
        val id: Long = 0L,
    )

    data class Stats(
        val sampleCount: Int,
        val averageMs: Long?,
        val medianMs: Long?,
        val minMs: Long?,
        val maxMs: Long?,
        val quality: SampleQuality,
    )

    data class Comparison(
        val currentMs: Long,
        val averageMs: Long,
        val deltaMs: Long,
    )

    data class OverlayInsight(
        val anchorAtEpochMs: Long?,
        val firstOccurrence: Boolean,
        val currentElapsedMs: Long?,
        val recentCompletedIntervalsMs: List<Long>,
        val stats: Stats,
        val comparison: Comparison?,
    )

    /**
     * Calculates consecutive non-negative intervals for one stable target key.
     * Equal timestamps are ordered by id so the result remains deterministic.
     */
    fun intervalsForKey(events: List<Event>, key: String): List<Long> {
        return events
            .asSequence()
            .filter { it.key == key }
            .sortedWith(compareBy<Event> { it.occurredAtEpochMs }.thenBy { it.id })
            .toList()
            .zipWithNext { previous, current -> current.occurredAtEpochMs - previous.occurredAtEpochMs }
            .filter { it >= 0L }
    }

    fun recentCompletedIntervals(
        intervalsMs: List<Long>,
        limit: Int = DEFAULT_OVERLAY_HISTORY_LIMIT,
    ): List<Long> {
        if (limit <= 0) return emptyList()
        return intervalsMs
            .asSequence()
            .filter { it >= 0L }
            .toList()
            .takeLast(limit)
    }

    fun statsFor(intervalsMs: List<Long>): Stats {
        val values = intervalsMs.filter { it >= 0L }.sorted()
        if (values.isEmpty()) {
            return Stats(
                sampleCount = 0,
                averageMs = null,
                medianMs = null,
                minMs = null,
                maxMs = null,
                quality = SampleQuality.NoSample,
            )
        }

        return Stats(
            sampleCount = values.size,
            averageMs = average(values),
            medianMs = median(values),
            minMs = values.first(),
            maxMs = values.last(),
            quality = if (values.size == 1) SampleQuality.Limited else SampleQuality.Ready,
        )
    }

    fun overlayInsight(
        anchorAtEpochMs: Long?,
        firstOccurrence: Boolean,
        recentCompletedIntervalsMs: List<Long>,
        nowEpochMs: Long,
        historyLimit: Int = DEFAULT_OVERLAY_HISTORY_LIMIT,
    ): OverlayInsight {
        val recent = recentCompletedIntervals(recentCompletedIntervalsMs, historyLimit)
        val stats = statsFor(recent)
        val currentElapsedMs = anchorAtEpochMs?.let { elapsedMs(it, nowEpochMs) }
        val comparison = if (currentElapsedMs != null && stats.averageMs != null) {
            Comparison(
                currentMs = currentElapsedMs,
                averageMs = stats.averageMs,
                deltaMs = currentElapsedMs - stats.averageMs,
            )
        } else {
            null
        }
        return OverlayInsight(
            anchorAtEpochMs = anchorAtEpochMs,
            firstOccurrence = firstOccurrence,
            currentElapsedMs = currentElapsedMs,
            recentCompletedIntervalsMs = recent,
            stats = stats,
            comparison = comparison,
        )
    }

    fun elapsedMs(anchorAtEpochMs: Long, nowEpochMs: Long): Long {
        return (nowEpochMs - anchorAtEpochMs).coerceAtLeast(0L)
    }

    fun chooseAxisUnit(maxIntervalMs: Long): AxisUnit {
        val value = maxIntervalMs.coerceAtLeast(0L)
        return when {
            value >= AxisUnit.Days.divisorMs -> AxisUnit.Days
            value >= AxisUnit.Hours.divisorMs -> AxisUnit.Hours
            value >= AxisUnit.Minutes.divisorMs -> AxisUnit.Minutes
            else -> AxisUnit.Seconds
        }
    }

    fun formatAxisValue(intervalMs: Long, unit: AxisUnit): String {
        val value = intervalMs.coerceAtLeast(0L).toDouble() / unit.divisorMs
        return if (value >= 10.0 || value == value.roundToLong().toDouble()) {
            "${value.roundToLong()}${unit.suffix}"
        } else {
            "${"%.1f".format(Locale.ROOT, value)}${unit.suffix}"
        }
    }

    fun formatDurationCompact(durationMs: Long): String {
        val totalSeconds = elapsedMs(0L, durationMs) / 1_000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds / 3_600L) % 24L
        val minutes = (totalSeconds / 60L) % 60L
        val seconds = totalSeconds % 60L
        return when {
            days > 0L -> "${days}天 ${hours.toString().padStart(2, '0')}小时"
            hours > 0L -> "${hours}小时 ${minutes.toString().padStart(2, '0')}分"
            minutes > 0L -> "${minutes}分 ${seconds.toString().padStart(2, '0')}秒"
            else -> "${seconds}秒"
        }
    }

    fun formatDurationClock(durationMs: Long): String {
        val totalSeconds = elapsedMs(0L, durationMs) / 1_000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds / 3_600L) % 24L
        val minutes = (totalSeconds / 60L) % 60L
        val seconds = totalSeconds % 60L
        val clock = "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        return if (days > 0L) "${days}天 $clock" else clock
    }

    private fun average(values: List<Long>): Long {
        val sum = values.fold(BigInteger.ZERO) { acc, value ->
            acc.add(BigInteger.valueOf(value))
        }
        return sum.divide(BigInteger.valueOf(values.size.toLong())).longValueExact()
    }

    private fun median(sortedValues: List<Long>): Long {
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middle]
        } else {
            BigInteger.valueOf(sortedValues[middle - 1])
                .add(BigInteger.valueOf(sortedValues[middle]))
                .divide(BigInteger.TWO)
                .longValueExact()
        }
    }
}
