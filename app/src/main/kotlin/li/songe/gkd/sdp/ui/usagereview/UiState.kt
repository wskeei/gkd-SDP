@file:JvmName("UsageReviewUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy

sealed interface DigitalSelfDisciplineReviewUiState {
    data object Loading : DigitalSelfDisciplineReviewUiState
    @Immutable
    data class Ready(val summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) : DigitalSelfDisciplineReviewUiState
    data object Error : DigitalSelfDisciplineReviewUiState
}

@Immutable
data class UsageGuardReviewPageUiState(
    val selectedRange: DigitalSelfDisciplineReviewPolicy.Range = DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
    val selectedType: DigitalSelfDisciplineReviewPolicy.ReviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
    val selectedFilter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
    val selectedMetric: DigitalSelfDisciplineReviewPolicy.ReviewMetric = DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
    val selectedTabIndex: Int = 0,
    val reviewState: DigitalSelfDisciplineReviewUiState = DigitalSelfDisciplineReviewUiState.Loading,
)

sealed interface UsageGuardReviewAction {
    data class UpdateRange(
        val range: DigitalSelfDisciplineReviewPolicy.Range,
    ) : UsageGuardReviewAction

    data class UpdateReviewType(
        val type: DigitalSelfDisciplineReviewPolicy.ReviewType,
    ) : UsageGuardReviewAction

    data class UpdateMetric(
        val metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ) : UsageGuardReviewAction

    data class UpdateInterceptFilter(
        val filter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter,
    ) : UsageGuardReviewAction
}
