package li.songe.gkd.sdp.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.songe.gkd.sdp.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
    val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
    val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
    val weeks = days / 7
    val months = (days / 30)
    val years = (days / 365)
    return when {
        // i18n-ignore: legacy fallback or non-display heuristic data
        years > 0 -> "${years}年前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        months > 0 -> "${months}月前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        weeks > 0 -> "${weeks}周前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        days > 0 -> "${days}天前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        hours > 0 -> "${hours}小时前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        minutes > 0 -> "${minutes}分钟前"
        // i18n-ignore: legacy fallback or non-display heuristic data
        else -> "刚刚"
    }
}

fun formatTimeAgo(timestamp: Long, context: Context): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
    val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
    val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
    val weeks = days / 7
    val months = days / 30
    val years = days / 365
    return when {
        years > 0 -> context.getString(R.string.time_years_ago, years)
        months > 0 -> context.getString(R.string.time_months_ago, months)
        weeks > 0 -> context.getString(R.string.time_weeks_ago, weeks)
        days > 0 -> context.getString(R.string.time_days_ago, days)
        hours > 0 -> context.getString(R.string.time_hours_ago, hours)
        minutes > 0 -> context.getString(R.string.time_minutes_ago, minutes)
        else -> context.getString(R.string.time_just_now)
    }
}

private val formatDateMap by lazy { hashMapOf<String, SimpleDateFormat>() }

fun Long.format(formatStr: String): String {
    var df = formatDateMap[formatStr]
    if (df == null) {
        df = SimpleDateFormat(formatStr, Locale.getDefault())
        formatDateMap[formatStr] = df
    }
    return df.format(this)
}

data class ThrottleTimer(
    private val interval: Long = 500L,
) {
    private var lastAccessTime: Long = 0L
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - lastAccessTime > interval) {
            lastAccessTime = t
            return true
        }
        return false
    }
}

@Composable
fun throttle(
    fn: (() -> Unit),
): (() -> Unit) {
    val timer = remember { ThrottleTimer() }
    return remember(fn) {
        {
            if (timer.expired()) {
                fn.invoke()
            }
        }
    }
}

@Composable
fun <T> throttle(
    fn: ((T) -> Unit),
): ((T) -> Unit) {
    val timer = remember { ThrottleTimer() }
    return remember(fn) {
        {
            if (timer.expired()) {
                fn.invoke(it)
            }
        }
    }
}
