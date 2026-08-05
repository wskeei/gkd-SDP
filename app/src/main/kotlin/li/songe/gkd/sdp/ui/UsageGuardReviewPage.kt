package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
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
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineTrendChart
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.ui.style.AppTheme
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
    private val rangeFlow = MutableStateFlow(DigitalSelfDisciplineReviewPolicy.Range.Today)
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
    ) { range, reviewType, interceptFilter, metric, today ->
        ReviewSelection(range, reviewType, interceptFilter, metric, today.date, today.zoneId)
    }.flatMapLatest { selection ->
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(selection.range, selection.today, selection.zoneId)
        val previousBounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            selection.range,
            selection.today.minusDays(selection.range.days),
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
        val today: LocalDate,
        val zoneId: ZoneId,
    )

    private data class ReviewClock(val date: LocalDate, val zoneId: ZoneId)

    private fun reviewClock() = ReviewClock(LocalDate.now(), ZoneId.systemDefault())
}

@Serializable
data object UsageGuardReviewRoute : NavKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsageGuardReviewPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardReviewVm>()
    val selectedRange by vm.selectedRangeFlow.collectAsState()
    val selectedType by vm.selectedReviewTypeFlow.collectAsState()
    val selectedFilter by vm.selectedInterceptFilterFlow.collectAsState()
    val selectedMetric by vm.selectedMetricFlow.collectAsState()
    val reviewState by vm.reviewUiStateFlow.collectAsState()
    var selectedTab by remember(selectedType) {
        mutableIntStateOf(if (selectedType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) 0 else 1)
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(PerfIcon.ArrowBack, onClick = { mainVm.popPage() })
                },
                title = { Text("数字自律复盘") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filters") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().itemPadding(),
                    colors = surfaceCardColors,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            DigitalSelfDisciplineReviewPolicy.ReviewType.entries.forEachIndexed { index, type ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = {
                                        selectedTab = index
                                        vm.updateReviewType(type)
                                    },
                                    text = { Text(type.label) },
                                )
                            }
                        }
                        Text("复盘范围", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DigitalSelfDisciplineReviewPolicy.Range.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { vm.updateRange(range) },
                                    label = { Text(range.label) },
                                )
                            }
                        }
                        if (DigitalSelfDisciplineReviewPresentation.showInterceptFilters(selectedType)) {
                            Text("拦截类型", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { vm.updateInterceptFilter(filter) },
                                        label = { Text(filter.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            when (val state = reviewState) {
                DigitalSelfDisciplineReviewUiState.Loading -> item(key = "loading") {
                    ReviewSectionCard("正在读取复盘数据", "统计只在本机生成，不会上传。") {
                        Text("请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is DigitalSelfDisciplineReviewUiState.Error -> item(key = "error") {
                    ReviewSectionCard("暂时无法读取复盘", "原有使用申请功能仍可正常使用。") {
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is DigitalSelfDisciplineReviewUiState.Ready -> {
                    val page = DigitalSelfDisciplineReviewPresentation.page(state.summary, selectedMetric)
                    item(key = "overview") { OverviewCard(page) }
                    item(key = "trend") {
                        ReviewSectionCard("趋势 · ${page.trend.metricLabel}", "本期与上一周期保持同一口径") {
                            if (selectedType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
                                        onClick = { vm.updateMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) },
                                        label = { Text("间用比") },
                                    )
                                    FilterChip(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
                                        onClick = { vm.updateMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP) },
                                        label = { Text("未使用间隔") },
                                    )
                                }
                            }
                            DigitalSelfDisciplineTrendChart(page.trend)
                        }
                    }
                    page.distributions.forEach { (title, bars) ->
                        item(key = "distribution_$title") {
                            ReviewRankedBarList(title, bars)
                        }
                    }
                    item(key = "recent") { RecentRowsCard(page.recentRows) }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(page: DigitalSelfDisciplineReviewPresentation.PagePresentation) {
    ReviewSectionCard("数据概览", page.coverage.text) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.overview.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { card -> MetricBlock(card.label, card.value, Modifier.weight(1f)) }
                            if (row.size == 1) MetricBlock("", "", Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    page.overview.forEach { card -> MetricBlock(card.label, card.value, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ReviewRankedBarList(
    title: String,
    bars: List<DigitalSelfDisciplineReviewPresentation.RankedBar>,
) {
    ReviewSectionCard(title, "按数量排序，比例同时用文字和进度表达") {
        if (bars.isEmpty()) {
            Text("所选范围暂无分布数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            bars.forEach { bar ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bar.label, modifier = Modifier.weight(1f))
                        Text("${bar.countText} · ${bar.shareText}")
                    }
                    LinearProgressIndicator(
                        progress = { bar.share },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRowsCard(rows: List<DigitalSelfDisciplineReviewPresentation.RecentRow>) {
    ReviewSectionCard("最近明细", "最多显示最近 10 条，不包含申请理由、网址或选择器文本") {
        if (rows.isEmpty()) {
            Text("所选范围暂无明细", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(row.primaryText, fontWeight = FontWeight.SemiBold)
                    Text(row.secondaryText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().itemPadding(),
        colors = surfaceCardColors,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UsageGuardReviewPagePreviewCompact() {
    AppTheme { Text("数字自律复盘 · 申请空态", modifier = Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, widthDp = 600, fontScale = 2f)
@Composable
private fun UsageGuardReviewPagePreviewWide() {
    AppTheme { Text("数字自律复盘 · 复盘筛选与趋势", modifier = Modifier.padding(16.dp)) }
}
