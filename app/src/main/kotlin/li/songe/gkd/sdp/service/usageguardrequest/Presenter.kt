@file:JvmName("UsageGuardRequestPresenter")

package li.songe.gkd.sdp.service

import androidx.compose.runtime.MutableState
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.usage.UsageRequestPresenter
import li.songe.gkd.sdp.usage.UsageRequestUiState
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy

internal data class UsageGuardRequestFormState(
    val selectedTags: MutableState<Set<String>>,
    val reasonText: MutableState<String>,
    val selectedDuration: MutableState<Int>,
    val customMinutesText: MutableState<String>,
    val newTagText: MutableState<String>,
    val showAddTagEditor: MutableState<Boolean>,
    val showCustomDuration: MutableState<Boolean>,
    val reasonError: MutableState<String?>,
    val durationError: MutableState<String?>,
    val tagsError: MutableState<String?>,
)

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

internal fun UsageGuardRequestUiState.reduce(action: UsageGuardRequestAction): UsageGuardRequestUiState = when (action) {
    UsageGuardRequestAction.Cancel,
    UsageGuardRequestAction.Submit,
    -> this
}
