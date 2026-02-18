package li.songe.gkd.sdp.util

object FocusTimeFormatter {
    fun formatRemainingText(endTime: Long, now: Long = System.currentTimeMillis()): String? {
        if (endTime <= 0L) return null
        val remainingMs = endTime - now
        if (remainingMs <= 0L) return null

        val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0L -> "剩余 ${hours} 小时 ${minutes} 分钟"
            minutes > 0L -> "剩余 ${minutes} 分钟 ${seconds} 秒"
            else -> "剩余 ${seconds} 秒"
        }
    }
}
