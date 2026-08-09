@file:JvmName("UsageGuardRequestUiState")

package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import androidx.compose.runtime.MutableState

sealed interface UsageRequestDatasetState {
    data object Loading : UsageRequestDatasetState
    data class Ready(val data: SelfControlIntervalRepository.UsageRequestOverlayData) : UsageRequestDatasetState
    data object Unavailable : UsageRequestDatasetState
}

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
