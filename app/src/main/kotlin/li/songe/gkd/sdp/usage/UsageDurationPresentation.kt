package li.songe.gkd.sdp.usage

import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy
import java.util.Locale
import kotlin.math.abs

/**
 * Stable, locale-neutral presentation for request duration and ratio values.
 *
 * Duration keeps the same Long-millisecond input as the rest of the rhythm
 * policy so formatting never causes integer truncation.
 */
object UsageDurationPresentation {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds / 3_600L) % 24L
        val minutes = (totalSeconds / 60L) % 60L
        val seconds = totalSeconds % 60L
        return when {
            durationMs < MINUTE_MS -> "${seconds}秒"
            durationMs < HOUR_MS -> String.format(Locale.ROOT, "%d分 %02d秒", minutes, seconds)
            durationMs < DAY_MS -> String.format(Locale.ROOT, "%d小时 %02d分", hours, minutes)
            else -> String.format(Locale.ROOT, "%d天 %02d小时", days, hours)
        }
    }

    fun formatRatio(value: Double?): String =
        UsageRequestRhythmPolicy.formatRatio(value ?: return "—") ?: "—"

    fun ratioDelta(current: Double, baseline: Double): Double = abs(current - baseline)
}
