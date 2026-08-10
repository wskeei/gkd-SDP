@file:JvmName("UsageGuardRequestPresenter")

package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.usage.UsageRequestPresenter
import li.songe.gkd.sdp.usage.UsageRequestUiState
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy

internal fun usageRequestElapsedState(
    state: UsageRequestDatasetState,
    nowEpochMs: Long,
): SelfControlElapsedPolicy.ElapsedState {
    if (state is UsageRequestDatasetState.Loading) {
        return SelfControlElapsedPolicy.ElapsedState.Loading
    }
    if (state is UsageRequestDatasetState.Unavailable) {
        return SelfControlElapsedPolicy.ElapsedState.Unavailable
    }
    val data = (state as? UsageRequestDatasetState.Ready)?.data
    return when (
        UsageRequestPresenter.present(
            appId = "",
            appName = "",
            data = data,
            nowEpochMs = nowEpochMs,
        ).status
    ) {
        UsageRequestUiState.Status.FIRST -> SelfControlElapsedPolicy.ElapsedState.NoHistory
        UsageRequestUiState.Status.AVAILABLE -> SelfControlElapsedPolicy.ElapsedState.Running(
            anchorAtEpochMs = data?.previousLastUsageEndedAt ?: return SelfControlElapsedPolicy.ElapsedState.Unavailable,
            firstOccurrence = false,
        )
        UsageRequestUiState.Status.MISSING_ACTUAL_END ->
            SelfControlElapsedPolicy.ElapsedState.MissingActualEnd
        UsageRequestUiState.Status.UNAVAILABLE -> SelfControlElapsedPolicy.ElapsedState.Unavailable
    }
}
