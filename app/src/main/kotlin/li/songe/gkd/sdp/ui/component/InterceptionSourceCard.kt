package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import li.songe.gkd.sdp.data.SubsConfig

data class InterceptionSourcePresentation(
    val title: String,
    val lines: List<String>,
) {
    companion object {
        fun selector(snapshot: SelectorRuleSnapshot): InterceptionSourcePresentation {
            val lines = buildList {
                val subscriptionName = SelectorRuleSnapshot.normalizeLabel(snapshot.subscriptionName)
                    ?: "id=${snapshot.subsId}"
                val groupName = SelectorRuleSnapshot.normalizeLabel(snapshot.groupName)
                    ?: "规则组 ${snapshot.groupKey}"
                add("订阅：$subscriptionName · v${snapshot.subsVersion}")
                val groupType = when (snapshot.groupType) {
                    SubsConfig.AppGroupType -> "应用"
                    SubsConfig.GlobalGroupType -> "全局"
                    else -> "未知类型"
                }
                add("规则组：$groupName（$groupType，key=${snapshot.groupKey}）")
                add("具体规则：${snapshot.displayRuleIdentity()}")
                add("规则标识：groupType=${snapshot.groupType}, groupKey=${snapshot.groupKey}, " +
                    "index=${snapshot.ruleIndex}, ${snapshot.ruleKey?.let { "key=$it" } ?: "未设置 key"}")
                add("目标应用：${snapshot.appId}")
                SelectorRuleSnapshot.shortActivityId(snapshot.activityId)?.let { add("页面：$it") }
            }
            return InterceptionSourcePresentation(
                title = "拦截来源：选择器规则",
                lines = lines,
            )
        }

        fun url(ruleId: Long, ruleName: String?): InterceptionSourcePresentation {
            val safeName = ruleName?.trim().takeUnless { it.isNullOrEmpty() } ?: "网址规则"
            return InterceptionSourcePresentation(
                title = "拦截来源：网址规则",
                lines = listOf(
                    "规则：$safeName",
                    "规则编号：${String.format(Locale.ROOT, "%,d", ruleId)}",
                ),
            )
        }

        fun appBlocker(rule: BlockTimeRule): InterceptionSourcePresentation =
            InterceptionSourcePresentation(
                title = "拦截来源：应用时间规则",
                lines = listOf(
                    "规则 #${rule.id}",
                    "目标：${rule.targetId}",
                    "时段：${rule.formatTimeRange()}",
                    "日期：${rule.formatDaysOfWeek()}",
                    "模式：${rule.formatModeDescription()}",
                ),
            )

        fun unknown(): InterceptionSourcePresentation = InterceptionSourcePresentation(
            title = "拦截来源",
            lines = listOf("本次规则信息暂不可用"),
        )
    }
}

@Composable
fun InterceptionSourceCard(
    presentation: InterceptionSourcePresentation,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            presentation.lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
