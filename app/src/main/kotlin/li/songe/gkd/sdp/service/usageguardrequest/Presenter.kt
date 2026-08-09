@file:JvmName("UsageGuardRequestPresenter")

package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy

internal fun usageRequestElapsedState(
    state: UsageRequestDatasetState,
    nowEpochMs: Long,
): SelfControlElapsedPolicy.ElapsedState = when (state) {
    UsageRequestDatasetState.Loading -> SelfControlElapsedPolicy.ElapsedState.Loading
    UsageRequestDatasetState.Unavailable -> SelfControlElapsedPolicy.ElapsedState.Unavailable
    is UsageRequestDatasetState.Ready -> when (state.data.anchorStatus) {
        SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest ->
            SelfControlElapsedPolicy.ElapsedState.NoHistory

        SelfControlIntervalRepository.UsageGapAnchorStatus.Available -> {
            val anchorAt = state.data.previousLastUsageEndedAt
            if (anchorAt == null || anchorAt > nowEpochMs) {
                SelfControlElapsedPolicy.ElapsedState.Unavailable
            } else {
                SelfControlElapsedPolicy.ElapsedState.Running(
                    anchorAtEpochMs = anchorAt,
                    firstOccurrence = false,
                )
            }
        }

        SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd ->
            SelfControlElapsedPolicy.ElapsedState.MissingActualEnd
    }
}
