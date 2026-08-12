@file:JvmName("UsageReviewScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy

@Composable
fun UsageGuardReviewPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardReviewVm>()
    val selectedRange by vm.selectedRangeFlow.collectAsStateWithLifecycle()
    val selectedType by vm.selectedReviewTypeFlow.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedInterceptFilterFlow.collectAsStateWithLifecycle()
    val selectedMetric by vm.selectedMetricFlow.collectAsStateWithLifecycle()
    val reviewState by vm.reviewUiStateFlow.collectAsStateWithLifecycle()
    var selectedTabIndex by remember(selectedType) {
        mutableIntStateOf(
            if (selectedType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) 0 else 1,
        )
    }
    val uiState = UsageGuardReviewPageUiState(
        selectedRange = selectedRange,
        selectedType = selectedType,
        selectedFilter = selectedFilter,
        selectedMetric = selectedMetric,
        selectedTabIndex = selectedTabIndex,
        reviewState = reviewState,
    )

    UsageGuardReviewPageSections(
        uiState = uiState,
        onBack = mainVm::popPage,
        onSelectTab = { selectedTabIndex = it },
        onSelectType = { vm.dispatch(UsageGuardReviewAction.UpdateReviewType(it)) },
        onSelectRange = { vm.dispatch(UsageGuardReviewAction.UpdateRange(it)) },
        onSelectFilter = { vm.dispatch(UsageGuardReviewAction.UpdateInterceptFilter(it)) },
        onSelectMetric = { vm.dispatch(UsageGuardReviewAction.UpdateMetric(it)) },
    )
}
