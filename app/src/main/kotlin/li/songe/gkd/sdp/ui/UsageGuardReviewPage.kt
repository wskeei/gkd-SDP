package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SelfControlReviewChart
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.UsageGuardHistoryPolicy
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import java.time.LocalDate

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

    val selectedRangeFlow = rangeFlow
    val selectedReviewTypeFlow = reviewTypeFlow
    val selectedInterceptFilterFlow = interceptFilterFlow

    private val todayFlow = flow {
        var current = LocalDate.now()
        emit(current)
        while (true) {
            delay(60_000L)
            val next = LocalDate.now()
            if (next != current) {
                current = next
                emit(current)
            }
        }
    }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LocalDate.now(),
    )

    val reviewUiStateFlow = combine(
        rangeFlow,
        reviewTypeFlow,
        interceptFilterFlow,
        todayFlow,
    ) { range, reviewType, interceptFilter, today ->
        ReviewSelection(range, reviewType, interceptFilter, today)
    }.flatMapLatest { selection ->
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            selection.range,
            selection.today,
        )
        val previousBounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            selection.range,
            selection.today.minusDays(selection.range.days),
        )
        combine(
            repository.observeReviewSource(bounds.startAt, bounds.endAt),
            repository.observeReviewSource(previousBounds.startAt, previousBounds.endAt),
        ) { current, previous ->
            val previousSummary = DigitalSelfDisciplineReviewPolicy.summarize(
                records = previous.usageRecords,
                events = previous.interceptEvents,
                bounds = previousBounds,
                reviewType = selection.reviewType,
                interceptFilter = selection.interceptFilter,
            )
            DigitalSelfDisciplineReviewPolicy.summarize(
                records = current.usageRecords,
                events = current.interceptEvents,
                bounds = bounds,
                reviewType = selection.reviewType,
                interceptFilter = selection.interceptFilter,
                previousSummary = previousSummary,
            )
        }.map { summary ->
            DigitalSelfDisciplineReviewUiState.Ready(summary) as DigitalSelfDisciplineReviewUiState
        }.catch { error ->
            emit(
                DigitalSelfDisciplineReviewUiState.Error(
                    error.message ?: "复盘数据暂时不可用",
                )
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DigitalSelfDisciplineReviewUiState.Loading,
    )

    val usageGuardSummaryFlow = combine(rangeFlow, todayFlow) { range, today -> range to today }
        .flatMapLatest { (range, today) ->
            val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(range, today)
            DbSet.usageGuardRecordDao.queryByRequestedAtRange(bounds.startAt, bounds.endAt)
                .map { UsageGuardReviewPolicy.summarize(it) }
        }.stateInit(UsageGuardReviewPolicy.summarize(emptyList()))

    fun updateRange(range: DigitalSelfDisciplineReviewPolicy.Range) = rangeFlow.update { range }

    fun updateReviewType(type: DigitalSelfDisciplineReviewPolicy.ReviewType) {
        reviewTypeFlow.update { type }
        if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            interceptFilterFlow.update { DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All }
        }
    }

    fun updateInterceptFilter(filter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter) {
        interceptFilterFlow.update { filter }
    }

    private data class ReviewSelection(
        val range: DigitalSelfDisciplineReviewPolicy.Range,
        val reviewType: DigitalSelfDisciplineReviewPolicy.ReviewType,
        val interceptFilter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter,
        val today: LocalDate,
    )
}

/** Same route name retained for navigation compatibility. */
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
    val reviewState by vm.reviewUiStateFlow.collectAsState()
    val usageSummary by vm.usageGuardSummaryFlow.collectAsState()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
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
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("复盘范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalSelfDisciplineReviewPolicy.Range.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { vm.updateRange(range) },
                                    label = { Text(range.label) },
                                )
                            }
                        }
                        Text("复盘类型", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalSelfDisciplineReviewPolicy.ReviewType.entries.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { vm.updateReviewType(type) },
                                    label = { Text(type.label) },
                                )
                            }
                        }
                        if (DigitalSelfDisciplineReviewPresentation.showInterceptFilters(selectedType)) {
                            Text("拦截类型", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    item(key = "interval_summary") {
                        IntervalSummaryCard(state.summary)
                    }
                    item(key = "recent_intervals") {
                        RecentIntervalsCard(state.summary)
                    }
                    item(key = "targets") {
                        RankedTargetsCard(state.summary)
                    }
                }
            }

            item(key = "legacy_usage_summary") {
                UsageRequestSummaryCard(usageSummary, selectedRange)
            }
        }
    }
}

@Composable
private fun IntervalSummaryCard(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) {
    ReviewSectionCard(
        title = "${summary.reviewType.label}间隔",
        subtitle = "${summary.range.label} · 事件 ${summary.eventCount} 次 · 有效间隔 ${summary.stats.sampleCount} 个",
    ) {
        if (summary.stats.sampleCount == 0 || summary.stats.averageMs == null || summary.stats.medianMs == null) {
            Text("暂无已完成间隔，不把缺失数据当作 0。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ReviewSectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBlock("平均", SelfControlIntervalPolicy.formatDurationCompact(summary.stats.averageMs), Modifier.weight(1f))
            MetricBlock("中位数", SelfControlIntervalPolicy.formatDurationCompact(summary.stats.medianMs), Modifier.weight(1f))
            MetricBlock("范围", "${SelfControlIntervalPolicy.formatDurationCompact(summary.stats.minMs ?: 0L)}～${SelfControlIntervalPolicy.formatDurationCompact(summary.stats.maxMs ?: 0L)}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        SelfControlReviewChart(summary)
        Text(summary.comparison.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentIntervalsCard(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) {
    ReviewSectionCard("最近明细", "最多显示最近 10 个有效间隔，便于观察时间是否正在拉长。") {
        if (summary.recentIntervals.isEmpty()) {
            Text("暂无明细", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ReviewSectionCard
        }
        summary.recentIntervals.forEachIndexed { index, item ->
            CompactResultRow(
                label = "${index + 1}. ${item.label}",
                value = SelfControlIntervalPolicy.formatDurationCompact(item.intervalMs),
            )
            if (index != summary.recentIntervals.lastIndex) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun RankedTargetsCard(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) {
    ReviewSectionCard("高频目标", "按事件次数排序，帮助你看见最近最常触发的应用或规则。") {
        if (summary.rankedTargets.isEmpty()) {
            Text("暂无目标记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ReviewSectionCard
        }
        summary.rankedTargets.forEachIndexed { index, target ->
            CompactResultRow("${index + 1}. ${target.label}", "${target.count} 次")
            if (index != summary.rankedTargets.lastIndex) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun UsageRequestSummaryCard(
    summary: UsageGuardReviewPolicy.Summary,
    range: DigitalSelfDisciplineReviewPolicy.Range,
) {
    ReviewSectionCard("使用申请补充复盘 · ${range.label}", "保留原有申请记录、时长、标签和结束状态统计。") {
        val widgetSummary = UsageGuardReviewPolicy.widgetSummary(summary)
        Text(widgetSummary.title, style = MaterialTheme.typography.titleSmall)
        Text(widgetSummary.metric, color = MaterialTheme.colorScheme.primary)
        Text(widgetSummary.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBlock("申请", "${summary.requestCount} 次", Modifier.weight(1f))
            MetricBlock("时长", "${summary.totalRequestedMinutes} 分钟", Modifier.weight(1f))
            MetricBlock("高风险", summary.riskPeriod.label, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("高频模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LegacyRankingList("应用排行", summary.topApps)
        Spacer(Modifier.height(8.dp))
        LegacyRankingList("标签排行", summary.topTags)
        Spacer(Modifier.height(12.dp))
        Text("结束状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (summary.endReasonCounts.isEmpty()) {
            Text("暂无结束状态", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            summary.endReasonCounts.toList()
                .sortedByDescending { it.second }
                .forEachIndexed { index, (reason, count) ->
                    CompactResultRow(UsageGuardReviewPolicy.endReasonLabel(reason), "$count 次")
                    if (index != summary.endReasonCounts.size - 1) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
        }
    }
}

@Composable
private fun LegacyRankingList(
    title: String,
    items: List<UsageGuardReviewPolicy.RankedItem>,
) {
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    if (items.isEmpty()) {
        Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    items.take(5).forEachIndexed { index, item ->
        CompactResultRow("${index + 1}. ${item.label}", "${item.count} 次")
        if (index != minOf(items.lastIndex, 4)) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun CompactResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
