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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.UsageGuardHistoryPolicy
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import java.time.LocalDate

enum class UsageGuardReviewRange(val label: String) {
    Today("今日"),
    SevenDays("近 7 天"),
}

class UsageGuardReviewVm : BaseViewModel() {
    private val rangeFlow = MutableStateFlow(UsageGuardReviewRange.Today)
    val selectedRangeFlow = rangeFlow

    val summaryFlow = rangeFlow.flatMapLatest { range ->
        val today = LocalDate.now()
        val startDate = when (range) {
            UsageGuardReviewRange.Today -> today
            UsageGuardReviewRange.SevenDays -> today.minusDays(6)
        }
        val (startAt, _) = UsageGuardHistoryPolicy.dayRange(startDate)
        val (_, endAt) = UsageGuardHistoryPolicy.dayRange(today)
        DbSet.usageGuardRecordDao.queryByRequestedAtRange(startAt, endAt).map { records ->
            UsageGuardReviewPolicy.summarize(records)
        }
    }.stateInit(UsageGuardReviewPolicy.summarize(emptyList()))

    fun updateRange(range: UsageGuardReviewRange) {
        rangeFlow.update { range }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Serializable
data object UsageGuardReviewRoute : NavKey

@Composable
fun UsageGuardReviewPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardReviewVm>()
    val selectedRange by vm.selectedRangeFlow.collectAsState()
    val summary by vm.summaryFlow.collectAsState()
    val widgetSummary = UsageGuardReviewPolicy.widgetSummary(summary)

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
            item(key = "range") {
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
                            UsageGuardReviewRange.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { vm.updateRange(range) },
                                    label = { Text(range.label) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "summary") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().itemPadding(),
                    colors = surfaceCardColors,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(widgetSummary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(widgetSummary.metric, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(widgetSummary.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricBlock(label = "申请", value = "${summary.requestCount} 次", modifier = Modifier.weight(1f))
                            MetricBlock(label = "时长", value = "${summary.totalRequestedMinutes} 分钟", modifier = Modifier.weight(1f))
                            MetricBlock(label = "高风险", value = summary.riskPeriod.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "apps_tags") {
                ReviewSectionCard(title = "高频模式", subtitle = "先看你把通行证交给了谁，以及通常用什么理由说服自己。") {
                    RankingList(title = "应用排行", items = summary.topApps.take(5), emptyText = "暂无应用记录")
                    Spacer(modifier = Modifier.height(14.dp))
                    RankingList(title = "标签排行", items = summary.topTags.take(5), emptyText = "暂无标签记录")
                }
            }

            item(key = "end_reasons") {
                ReviewSectionCard(title = "结束状态", subtitle = "结束方式能反映这次申请是自然到时、主动离开，还是被新的申请打断。") {
                    if (summary.endReasonCounts.isEmpty()) {
                        Text("暂无结束状态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        summary.endReasonCounts.toList()
                            .sortedByDescending { it.second }
                            .forEachIndexed { index, (reason, count) ->
                                CompactResultRow(
                                    label = UsageGuardReviewPolicy.endReasonLabel(reason),
                                    value = "$count 次",
                                )
                                if (index != summary.endReasonCounts.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                }
                            }
                    }
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
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun RankingList(
    title: String,
    items: List<UsageGuardReviewPolicy.RankedItem>,
    emptyText: String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    if (items.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    items.forEachIndexed { index, item ->
        CompactResultRow(
            label = "${index + 1}. ${item.label}",
            value = "${item.count} 次",
        )
        if (index != items.lastIndex) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun CompactResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
