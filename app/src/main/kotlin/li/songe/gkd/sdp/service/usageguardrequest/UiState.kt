@file:JvmName("UsageGuardRequestUiState")

package li.songe.gkd.sdp.service
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.SelfControlIntervalRepository

sealed interface UsageRequestDatasetState {
    data object Loading : UsageRequestDatasetState
    data class Ready(val data: SelfControlIntervalRepository.UsageRequestOverlayData) : UsageRequestDatasetState
    data object Unavailable : UsageRequestDatasetState
}

@Immutable
data class UsageGuardRequestUiState(
    val dataset: UsageRequestDatasetState = UsageRequestDatasetState.Loading,
)

sealed interface UsageGuardRequestAction {
    data object Cancel : UsageGuardRequestAction
    data object Submit : UsageGuardRequestAction
}
