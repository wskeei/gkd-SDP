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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineTrendChart
import li.songe.gkd.sdp.ui.component.LocalizedPage
import li.songe.gkd.sdp.ui.component.LocalizedRankedBar
import li.songe.gkd.sdp.ui.component.LocalizedRecentRow
import li.songe.gkd.sdp.ui.component.labelRes
import li.songe.gkd.sdp.ui.component.localizedReviewPage
import li.songe.gkd.sdp.ui.component.render
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsageGuardReviewPageSections(
    uiState: UsageGuardReviewPageUiState,
    onBack: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectType: (DigitalSelfDisciplineReviewPolicy.ReviewType) -> Unit,
    onSelectRange: (DigitalSelfDisciplineReviewPolicy.Range) -> Unit,
    onSelectFilter: (DigitalSelfDisciplineReviewPolicy.InterceptKindFilter) -> Unit,
    onSelectMetric: (DigitalSelfDisciplineReviewPolicy.ReviewMetric) -> Unit,
) {
    val selectedTab = uiState.selectedTabIndex
    val selectedType = uiState.selectedType
    val selectedRange = uiState.selectedRange
    val selectedFilter = uiState.selectedFilter
    val selectedMetric = uiState.selectedMetric
    val reviewState = uiState.reviewState

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(PerfIcon.ArrowBack, onClick = onBack)
                },
                title = {
                    Text(
                        text = stringResource(R.string.s_c7380c3c20),
                        modifier = Modifier.testTag("usage_guard_review_title"),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filters") {
                UsageGuardReviewFilters(
                    selectedTab = selectedTab,
                    selectedType = selectedType,
                    selectedRange = selectedRange,
                    selectedFilter = selectedFilter,
                    onSelectTab = onSelectTab,
                    onSelectType = onSelectType,
                    onSelectRange = onSelectRange,
                    onSelectFilter = onSelectFilter,
                )
            }
            when (val state = reviewState) {
                DigitalSelfDisciplineReviewUiState.Loading -> item(key = "loading") {
                    ReviewSectionCard(
                        stringResource(R.string.usage_review_loading_title),
                        stringResource(R.string.usage_review_loading_desc),
                    ) {
                        Text(stringResource(R.string.s_3b39242124), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DigitalSelfDisciplineReviewUiState.Error -> item(key = "error") {
                    ReviewSectionCard(
                        stringResource(R.string.usage_review_error_title),
                        stringResource(R.string.usage_review_error_desc),
                    ) {
                        Text(stringResource(R.string.usage_review_error_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is DigitalSelfDisciplineReviewUiState.Ready -> {
                    val page = localizedReviewPage(state.summary, selectedMetric)
                    item(key = "overview") { OverviewCard(page) }
                    item(key = "trend") {
                        ReviewSectionCard(
                            stringResource(R.string.usage_review_trend_title, stringResource(page.trend.metricRes)),
                            stringResource(R.string.usage_review_trend_desc),
                        ) {
                            if (selectedType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
                                        onClick = {
                                            onSelectMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .semantics {
                                                stateDescription = if (selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_selected_state,
                                        li.songe.gkd.sdp.app.getString(
                                            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO.labelRes(),
                                        ),
                                    )
                                } else {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_unselected_state,
                                        li.songe.gkd.sdp.app.getString(
                                            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO.labelRes(),
                                        ),
                                    )
                                }
                                            },
                                        label = {
                                            Text(
                                                stringResource(
                                                    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO.labelRes(),
                                                ),
                                            )
                                        },
                                    )
                                    SegmentedButton(
                                        selected = selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
                                        onClick = {
                                            onSelectMetric(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .semantics {
                                                stateDescription = if (selectedMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP) {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_selected_state,
                                        li.songe.gkd.sdp.app.getString(
                                            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP.labelRes(),
                                        ),
                                    )
                                } else {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_unselected_state,
                                        li.songe.gkd.sdp.app.getString(
                                            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP.labelRes(),
                                        ),
                                    )
                                }
                                            },
                                        label = {
                                            Text(
                                                stringResource(
                                                    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP.labelRes(),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                            DigitalSelfDisciplineTrendChart(page.trend)
                        }
                    }
                    page.distributions.forEachIndexed { index, distribution ->
                        item(key = "distribution_$index") {
                            ReviewRankedBarList(distribution.titleRes, distribution.bars)
                        }
                    }
                    item(key = "recent") { RecentRowsCard(page.recentRows) }
                }
            }
        }
    }
}

@Composable
private fun UsageGuardReviewFilters(
    selectedTab: Int,
    selectedType: DigitalSelfDisciplineReviewPolicy.ReviewType,
    selectedRange: DigitalSelfDisciplineReviewPolicy.Range,
    selectedFilter: DigitalSelfDisciplineReviewPolicy.InterceptKindFilter,
    onSelectTab: (Int) -> Unit,
    onSelectType: (DigitalSelfDisciplineReviewPolicy.ReviewType) -> Unit,
    onSelectRange: (DigitalSelfDisciplineReviewPolicy.Range) -> Unit,
    onSelectFilter: (DigitalSelfDisciplineReviewPolicy.InterceptKindFilter) -> Unit,
) {
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
                            onSelectTab(index)
                            onSelectType(type)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                stateDescription = if (selectedTab == index) {
                                    li.songe.gkd.sdp.app.getString(R.string.review_selected_plain)
                                } else {
                                    li.songe.gkd.sdp.app.getString(R.string.review_unselected_plain)
                                }
                            },
                        text = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            Text(stringResource(R.string.s_302471b81d), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DigitalSelfDisciplineReviewPolicy.Range.entries.forEach { range ->
                    SegmentedButton(
                        selected = selectedRange == range,
                        onClick = { onSelectRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = range.ordinal,
                            count = DigitalSelfDisciplineReviewPolicy.Range.entries.size,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .semantics {
                                stateDescription = if (selectedRange == range) {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_selected_state,
                                        li.songe.gkd.sdp.app.getString(range.labelRes()),
                                    )
                                } else {
                                    li.songe.gkd.sdp.app.getString(
                                        R.string.review_unselected_state,
                                        li.songe.gkd.sdp.app.getString(range.labelRes()),
                                    )
                                }
                            },
                        label = { Text(stringResource(range.labelRes())) },
                    )
                }
            }
            if (DigitalSelfDisciplineReviewPresentation.showInterceptFilters(selectedType)) {
                Text(stringResource(R.string.s_1278802e6e), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onSelectFilter(filter) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    stateDescription = if (selectedFilter == filter) {
                                        li.songe.gkd.sdp.app.getString(
                                            R.string.review_selected_state,
                                            li.songe.gkd.sdp.app.getString(filter.labelRes()),
                                        )
                                    } else {
                                        li.songe.gkd.sdp.app.getString(
                                            R.string.review_unselected_state,
                                            li.songe.gkd.sdp.app.getString(filter.labelRes()),
                                        )
                                    }
                                },
                            label = { Text(stringResource(filter.labelRes())) },
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun OverviewCard(page: LocalizedPage) {
    ReviewSectionCard(stringResource(R.string.usage_review_overview_title), page.coverage.render()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.overview.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { card ->
                                MetricBlock(card.labelRes, card.value.render(), Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    page.overview.forEach { card ->
                        MetricBlock(card.labelRes, card.value.render(), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}


@Composable
internal fun ReviewRankedBarList(
    titleRes: Int,
    bars: List<LocalizedRankedBar>,
) {
    ReviewSectionCard(stringResource(titleRes), stringResource(R.string.usage_review_ranked_desc)) {
        if (bars.isEmpty()) {
            Text(stringResource(R.string.s_6c53cda00c), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            bars.forEach { bar ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            bar.labelRes?.let { stringResource(it) } ?: bar.label,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(
                                R.string.s_dda4842aee,
                                stringResource(R.string.review_count_times, bar.count),
                                stringResource(
                                    R.string.review_percent,
                                    String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        bar.share * 100f,
                                    ),
                                ),
                            ),
                        )
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
internal fun RecentRowsCard(rows: List<LocalizedRecentRow>) {
    ReviewSectionCard(
        stringResource(R.string.usage_review_recent_title),
        stringResource(R.string.usage_review_recent_desc),
    ) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.s_fa8cdd2a46), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(row.primary.render(), fontWeight = FontWeight.SemiBold)
                    Text(row.secondary.render(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun MetricBlock(labelRes: Int, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (labelRes != 0) {
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
