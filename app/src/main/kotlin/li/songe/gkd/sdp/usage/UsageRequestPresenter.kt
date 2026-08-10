package li.songe.gkd.sdp.usage

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

/** Maps interval repository state into immutable usage-request UI state. */
object UsageRequestPresenter {
    fun present(
        appId: String,
        appName: String,
        data: SelfControlIntervalRepository.UsageRequestOverlayData?,
        nowEpochMs: Long,
        selectedTags: Set<String> = emptySet(),
        reason: String = "",
        requestedDurationMinutes: Int = 0,
    ): UsageRequestUiState {
        val status = when {
            data == null -> UsageRequestUiState.Status.UNAVAILABLE
            data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest ->
                UsageRequestUiState.Status.FIRST
            data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd ->
                UsageRequestUiState.Status.MISSING_ACTUAL_END
            data.previousLastUsageEndedAt == null ||
                data.previousLastUsageEndedAt > nowEpochMs ->
                UsageRequestUiState.Status.UNAVAILABLE
            else -> UsageRequestUiState.Status.AVAILABLE
        }
        return UsageRequestUiState(
            appId = appId,
            appName = appName,
            selectedTags = selectedTags,
            reason = reason,
            requestedDurationMinutes = requestedDurationMinutes,
            lastUsageEndedAt = data?.previousLastUsageEndedAt,
            nowEpochMs = nowEpochMs,
            status = status,
        )
    }

    fun currentGapMs(
        data: SelfControlIntervalRepository.UsageRequestOverlayData?,
        nowEpochMs: Long,
    ): Long? = data?.previousLastUsageEndedAt?.let {
        UsageRequestRhythmPolicy.gapMs(it, nowEpochMs)
    }
}
