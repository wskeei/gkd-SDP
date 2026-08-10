@file:JvmName("UsageReviewSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineTrendChart
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import java.time.LocalDate
import java.time.ZoneId
import li.songe.gkd.sdp.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsageGuardReviewPageSections() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardReviewVm>()
    val selectedRange by vm.selectedRangeFlow.collectAsStateWithLifecycle()
    val selectedType by vm.selectedReviewTypeFlow.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedInterceptFilterFlow.collectAsStateWithLifecycle()
    val selectedMetric by vm.selectedMetricFlow.collectAsStateWithLifecycle()
    val reviewState by vm.reviewUiStateFlow.collectAsStateWithLifecycle()
    var selectedTab by remember(selectedType) {
        mutableIntStateOf(if (selectedType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) 0 else 1)
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(PerfIcon.ArrowBack, onClick = { mainVm.popPage() })
                },
                title = { Text(li.songe.gkd.sdp.app.getString(R.string.s_c7380c3c20)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filters") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).itemPadding(),
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
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .semantics { stateDescription = if (selectedTab == index) "已选择" else "未选择" },
                                    text = { Text(type.label) },
                                )
                            }
                        }
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_302471b81d), style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            DigitalSelfDisciplineReviewPolicy.Range.entries.forEach { range ->
                                SegmentedButton(
                                    selected = selectedRange == range,
                                    onClick = { vm.updateRange(range) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = range.ordinal,
                                        count = DigitalSelfDisciplineReviewPolicy.Range.entries.size,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .semantics {
                                            stateDescription = if (selectedRange == range) "${range.label}，已选择" else "${range.label}，未选择"
                                        },
                                    label = { Text(range.label) },
                                )
                            }
                        }
                        if (DigitalSelfDisciplineReviewPresentation.showInterceptFilters(selectedType)) {
                            Text(li.songe.gkd.sdp.app.getString(R.string.s_1278802e6e), style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { vm.updateInterceptFilter(filter) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .semantics {
                                                stateDescription = if (selectedFilter == filter) "${filter.label}，已选择" else "${filter.label}，未选择"
                                            },
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
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_3b39242124), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
                                        onClick = { vm.updateMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .semantics {
                                                stateDescription = if (selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) "间用比，已选择" else "间用比，未选择"
                                            },
                                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_4cec547cf2)) },
                                    )
                                    SegmentedButton(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
                                        onClick = { vm.updateMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .semantics {
                                                stateDescription = if (selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP) "未使用间隔，已选择" else "未使用间隔，未选择"
                                            },
                                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_41d7664f15)) },
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
internal fun OverviewCard(page: DigitalSelfDisciplineReviewPresentation.PagePresentation) {
    ReviewSectionCard("数据概览", page.coverage.text) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.overview.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { card -> MetricBlock(card.label, card.value, Modifier.weight(1f)) }
                            if (row.size == 1) MetricBlock("", "", Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    page.overview.forEach { card -> MetricBlock(card.label, card.value, Modifier.weight(1f)) }
                }
            }
        }
    }
}


@Composable
internal fun ReviewRankedBarList(
    title: String,
    bars: List<DigitalSelfDisciplineReviewPresentation.RankedBar>,
) {
    ReviewSectionCard(title, "按数量排序，比例同时用文字和进度表达") {
        if (bars.isEmpty()) {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_6c53cda00c), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            bars.forEach { bar ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bar.label, modifier = Modifier.weight(1f))
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_dda4842aee, (bar.countText).toString(), (bar.shareText).toString()))
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
internal fun RecentRowsCard(rows: List<DigitalSelfDisciplineReviewPresentation.RecentRow>) {
    ReviewSectionCard("最近明细", "最多显示最近 10 条，不包含申请理由、网址或选择器文本") {
        if (rows.isEmpty()) {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_fa8cdd2a46), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun ReviewSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).itemPadding(),
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
internal fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}


@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun UsageGuardReviewPagePreviewUsageData() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = true)) }
}


@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun UsageGuardReviewPagePreviewUsageEmpty() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = false)) }
}


@Preview(showBackground = true, widthDp = 600)
@Composable
internal fun UsageGuardReviewPagePreviewInterceptData() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt, hasData = true)) }
}


@Preview(showBackground = true, widthDp = 600, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun UsageGuardReviewPagePreviewWideDarkLargeText() {
    AppTheme(invertedTheme = true) {
        ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = true))
    }
}


@Composable
internal fun ReviewPreviewContent(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) {
    val page = DigitalSelfDisciplineReviewPresentation.page(summary)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { OverviewCard(page) }
        item {
            ReviewSectionCard("趋势 · ${page.trend.metricLabel}", "合成数据预览") {
                DigitalSelfDisciplineTrendChart(page.trend)
            }
        }
        page.distributions.forEach { (title, bars) ->
            item { ReviewRankedBarList(title, bars) }
        }
        item { RecentRowsCard(page.recentRows) }
    }
}


internal fun previewSummary(
    type: DigitalSelfDisciplineReviewPolicy.ReviewType,
    hasData: Boolean,
): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
    val zone = ZoneId.of("Asia/Shanghai")
    val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
        DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
        LocalDate.of(2026, 8, 4).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(),
        zone,
    )
    val rows = if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest && hasData) {
        listOf(
            UsageReviewRow(1L, "demo.reader", "阅读应用", listOf("学习"), 30, bounds.startAt + 1_000L, 5, 90L * 60_000L),
            UsageReviewRow(2L, "demo.reader", "阅读应用", listOf("学习", "其他"), 45, bounds.startAt + 2_000L, 1, 150L * 60_000L),
        )
    } else {
        emptyList()
    }
    val events = if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt && hasData) {
        listOf(
            SelfControlAttemptEvent(1L, "demo.rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, "demo.rule", "示例规则", bounds.startAt + 1_000L, 30L * 60_000L),
            SelfControlAttemptEvent(2L, "demo.rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, "demo.rule", "示例规则", bounds.startAt + 2_000L, 45L * 60_000L),
        )
    } else {
        emptyList()
    }
    return DigitalSelfDisciplineReviewPolicy.summarize(
        usageRows = rows,
        events = events,
        bounds = bounds,
        reviewType = type,
        interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
        zoneId = zone,
    )
}
