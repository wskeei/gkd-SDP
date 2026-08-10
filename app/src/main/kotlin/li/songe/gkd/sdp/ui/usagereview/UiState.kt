@file:JvmName("UsageReviewUiState0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Row
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.runtime.appDependencies
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import java.time.LocalDate
import java.time.ZoneId

sealed interface DigitalSelfDisciplineReviewUiState {
    data object Loading : DigitalSelfDisciplineReviewUiState
    data class Ready(val summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) : DigitalSelfDisciplineReviewUiState
    data class Error(val message: String) : DigitalSelfDisciplineReviewUiState
}


class UsageGuardReviewVm : BaseViewModel() {
    private val repository by lazy { SelfControlIntervalRepository.fromDb() }
    private val rangeFlow = MutableStateFlow(DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS)
    private val reviewTypeFlow = MutableStateFlow(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest)
    private val interceptFilterFlow = MutableStateFlow(DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All)
    private val metricFlow = MutableStateFlow(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)

    val selectedRangeFlow = rangeFlow
    val selectedReviewTypeFlow = reviewTypeFlow
    val selectedInterceptFilterFlow = interceptFilterFlow
    val selectedMetricFlow = metricFlow

    private val todayFlow = flow {
        var current = reviewClock()
        emit(current)
        while (true) {
            delay(60_000L)
            val next = reviewClock()
            if (next != current) {
                current = next
                emit(current)
            }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, reviewClock())

    val reviewUiStateFlow = combine(
        rangeFlow,
        reviewTypeFlow,
        interceptFilterFlow,
        metricFlow,
        todayFlow,
    ) { range, reviewType, interceptFilter, metric, clock ->
        ReviewSelection(range, reviewType, interceptFilter, metric, clock.nowEpochMs, clock.zoneId)
    }.flatMapLatest { selection ->
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            selection.range,
            selection.nowEpochMs,
            selection.zoneId,
        )
        val previousBounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            selection.range,
            selection.nowEpochMs - selection.range.durationMs,
            selection.zoneId,
        )
        combine(
            repository.observeReviewSource(bounds.startAt, bounds.endAt),
            repository.observeReviewSource(previousBounds.startAt, previousBounds.endAt),
        ) { current, previous ->
            val previousSummary = DigitalSelfDisciplineReviewPolicy.summarize(
                usageRows = previous.usageRows,
                events = previous.interceptEvents,
                bounds = previousBounds,
                reviewType = selection.reviewType,
                interceptFilter = selection.interceptFilter,
                zoneId = selection.zoneId,
            )
            DigitalSelfDisciplineReviewPolicy.summarize(
                usageRows = current.usageRows,
                events = current.interceptEvents,
                bounds = bounds,
                reviewType = selection.reviewType,
                interceptFilter = selection.interceptFilter,
                zoneId = selection.zoneId,
                previousSummary = previousSummary,
            )
        }.map { summary ->
            DigitalSelfDisciplineReviewUiState.Ready(summary) as DigitalSelfDisciplineReviewUiState
        }.catch {
            emit(DigitalSelfDisciplineReviewUiState.Error("复盘数据暂时不可用"))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DigitalSelfDisciplineReviewUiState.Loading)

    fun updateRange(range: DigitalSelfDisciplineReviewPolicy.Range) = rangeFlow.update { range }

    fun updateReviewType(type: DigitalSelfDisciplineReviewPolicy.ReviewType) {
        reviewTypeFlow.update { type }
        metricFlow.update {
            DigitalSelfDisciplineReviewPresentation.defaultMetric(type)
        }
        if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            interceptFilterFlow.update { DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All }
        }
    }

    fun updateMetric(metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric) {
        if (reviewTypeFlow.value == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest &&
            metric != DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL
        ) {
            metricFlow.update { metric }
        }
    }

    fun updateInterceptFilter(filter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter) =
        interceptFilterFlow.update { filter }

    private data class ReviewSelection(
        val range: DigitalSelfDisciplineReviewPolicy.Range,
        val reviewType: DigitalSelfDisciplineReviewPolicy.ReviewType,
        val interceptFilter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter,
        val metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
        val nowEpochMs: Long,
        val zoneId: ZoneId,
    )

    private data class ReviewClock(val nowEpochMs: Long, val zoneId: ZoneId)

    private fun reviewClock() = ReviewClock(
        nowEpochMs = appDependencies.clock.nowEpochMillis(),
        zoneId = ZoneId.systemDefault(),
    )
}
