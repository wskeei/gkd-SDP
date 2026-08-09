package li.songe.gkd.sdp.runtime

/**
 * Converts a persisted wall-clock deadline into a process-local monotonic
 * deadline. Wall time remains the source of truth across process restarts;
 * elapsed time owns all in-process waiting and expiry decisions.
 */
object MonotonicDeadlinePolicy {
    fun deadlineFromWallClock(
        nowEpochMillis: Long,
        nowElapsedMillis: Long,
        wallDeadlineMillis: Long,
    ): Long {
        val remainingWallMillis = (wallDeadlineMillis - nowEpochMillis).coerceAtLeast(0L)
        return nowElapsedMillis + remainingWallMillis
    }

    fun remainingMillis(nowElapsedMillis: Long, deadlineElapsedMillis: Long): Long =
        (deadlineElapsedMillis - nowElapsedMillis).coerceAtLeast(0L)
}
