package li.songe.gkd.sdp.util

object UsageGuardCountdownOverlayLayoutPolicy {
    data class Position(
        val x: Int,
        val y: Int,
    )

    fun initialPosition(
        marginPx: Int,
        statusBarHeightPx: Int,
    ): Position {
        val safeMargin = marginPx.coerceAtLeast(0)
        return Position(
            x = safeMargin,
            y = maxOf(safeMargin, statusBarHeightPx.coerceAtLeast(0)),
        )
    }

    fun shouldResetPosition(
        previousAppId: String,
        previousRecordId: Long,
        nextAppId: String,
        nextRecordId: Long,
    ): Boolean {
        return previousAppId != nextAppId || previousRecordId != nextRecordId
    }
}
