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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import android.content.Context
import androidx.compose.ui.unit.dp
import java.util.Locale
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import li.songe.gkd.sdp.data.SubsConfig

@Immutable
data class InterceptionSourceLine(
    val resId: Int,
    val args: List<Any> = emptyList(),
)

@Immutable
data class InterceptionSourceArg(
    val resId: Int,
    val args: List<Any> = emptyList(),
)

@Immutable
data class InterceptionSourcePresentation(
    val title: String,
    val lines: List<String>,
    val titleRes: Int = R.string.interception_source_unknown_title,
    val localizedLines: List<InterceptionSourceLine> = emptyList(),
) {
    companion object {
        fun selector(snapshot: SelectorRuleSnapshot): InterceptionSourcePresentation {
            val lines = buildList {
                val subscriptionName = SelectorRuleSnapshot.normalizeLabel(snapshot.subscriptionName)
                    ?: "id=${snapshot.subsId}"
                val groupName = SelectorRuleSnapshot.normalizeLabel(snapshot.groupName)
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    ?: "规则组 ${snapshot.groupKey}"
                // i18n-ignore: legacy fallback or non-display heuristic data
                add("订阅：$subscriptionName · v${snapshot.subsVersion}")
                val groupType = when (snapshot.groupType) {
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    SubsConfig.AppGroupType -> "应用"
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    SubsConfig.GlobalGroupType -> "全局"
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    else -> "未知类型"
                }
                // i18n-ignore: legacy fallback or non-display heuristic data
                add("规则组：$groupName（$groupType，key=${snapshot.groupKey}）")
                // i18n-ignore: legacy fallback or non-display heuristic data
                add("具体规则：${snapshot.displayRuleIdentity()}")
                // i18n-ignore: legacy fallback or non-display heuristic data
                add("规则标识：groupType=${snapshot.groupType}, groupKey=${snapshot.groupKey}, " +
                    "index=${snapshot.ruleIndex}, ${snapshot.ruleKey?.let { "key=$it" } ?: "未设置 key"}")
                // i18n-ignore: legacy fallback or non-display heuristic data
                add("目标应用：${snapshot.appId}")
                // i18n-ignore: legacy fallback or non-display heuristic data
                SelectorRuleSnapshot.shortActivityId(snapshot.activityId)?.let { add("页面：$it") }
            }
            return InterceptionSourcePresentation(
                // i18n-ignore: legacy fallback or non-display heuristic data
                title = "拦截来源：选择器规则",
                lines = lines,
                titleRes = R.string.interception_source_selector_title,
                localizedLines = buildList {
                    add(
                        InterceptionSourceLine(
                            R.string.interception_source_selector_subscription,
                            listOf(
                                SelectorRuleSnapshot.normalizeLabel(snapshot.subscriptionName)
                                    ?: "id=${snapshot.subsId}",
                                snapshot.subsVersion,
                            ),
                        ),
                    )
                    val groupName = SelectorRuleSnapshot.normalizeLabel(snapshot.groupName)
                    val groupTypeRes = when (snapshot.groupType) {
                        SubsConfig.AppGroupType -> R.string.interception_source_group_type_app
                        SubsConfig.GlobalGroupType -> R.string.interception_source_group_type_global
                        else -> R.string.interception_source_group_type_unknown
                    }
                    add(
                        InterceptionSourceLine(
                            R.string.interception_source_group_line,
                            listOf(
                                groupName ?: InterceptionSourceArg(
                                    R.string.interception_source_rule_group_fallback,
                                    listOf(snapshot.groupKey),
                                ),
                                InterceptionSourceArg(groupTypeRes),
                                snapshot.groupKey,
                            ),
                        ),
                    )
                    add(
                        InterceptionSourceLine(
                            R.string.interception_source_rule_line,
                            listOf(
                                SelectorRuleSnapshot.normalizeLabel(snapshot.ruleName)
                                    ?: if (snapshot.ruleKey != null) {
                                        InterceptionSourceArg(
                                            R.string.rule_key_fallback,
                                            listOf(snapshot.ruleKey),
                                        )
                                    } else {
                                        InterceptionSourceArg(
                                            R.string.rule_index_fallback,
                                            listOf(snapshot.ruleIndex.coerceAtLeast(0) + 1),
                                        )
                                    },
                            ),
                        ),
                    )
                    if (snapshot.ruleKey == null) {
                        add(
                            InterceptionSourceLine(
                                R.string.interception_source_identity_without_key,
                                listOf(snapshot.groupType, snapshot.groupKey, snapshot.ruleIndex),
                            ),
                        )
                    } else {
                        add(
                            InterceptionSourceLine(
                                R.string.interception_source_identity_with_key,
                                listOf(
                                    snapshot.groupType,
                                    snapshot.groupKey,
                                    snapshot.ruleIndex,
                                    snapshot.ruleKey,
                                ),
                            ),
                        )
                    }
                    add(
                        InterceptionSourceLine(
                            R.string.interception_source_target_app,
                            listOf(snapshot.appId),
                        ),
                    )
                    SelectorRuleSnapshot.shortActivityId(snapshot.activityId)?.let {
                        add(InterceptionSourceLine(R.string.interception_source_page, listOf(it)))
                    }
                },
            )
        }

        fun url(ruleId: Long, ruleName: String?): InterceptionSourcePresentation {
            val safeName = ruleName?.trim().takeUnless { it.isNullOrEmpty() }
            return InterceptionSourcePresentation(
                // i18n-ignore: legacy fallback or non-display heuristic data
                title = "拦截来源：网址规则",
                lines = listOf(
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "规则：${safeName ?: "网址规则"}",
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "规则编号：${String.format(Locale.ROOT, "%,d", ruleId)}",
                ),
                titleRes = R.string.interception_source_url_title,
                localizedLines = listOf(
                    InterceptionSourceLine(
                        R.string.interception_source_url_rule_line,
                        listOf(
                            safeName ?: InterceptionSourceArg(
                                R.string.interception_source_url_rule_fallback,
                            ),
                        ),
                    ),
                    InterceptionSourceLine(
                        R.string.interception_source_url_id_line,
                        listOf(String.format(Locale.ROOT, "%,d", ruleId)),
                    ),
                ),
            )
        }

        fun appBlocker(
            rule: BlockTimeRule,
            context: Context? = null,
        ): InterceptionSourcePresentation =
            InterceptionSourcePresentation(
                // i18n-ignore: legacy fallback or non-display heuristic data
                title = "拦截来源：应用时间规则",
                lines = listOf(
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "规则 #${rule.id}",
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "目标：${if (rule.targetType == BlockTimeRule.TARGET_TYPE_GROUP) "应用组" else "应用"} · ${rule.targetId}",
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "时段：${rule.formatTimeRange()}",
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "日期：${rule.formatDaysOfWeek()}",
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "模式：${rule.formatModeDescription()}",
                ),
                titleRes = R.string.interception_source_app_title,
                localizedLines = if (context == null) {
                    emptyList()
                } else {
                    listOf(
                        InterceptionSourceLine(R.string.interception_source_app_rule_id, listOf(rule.id)),
                        if (rule.targetType == BlockTimeRule.TARGET_TYPE_GROUP) {
                            InterceptionSourceLine(
                                R.string.interception_source_app_target_group,
                                listOf(rule.targetId),
                            )
                        } else {
                            InterceptionSourceLine(
                                R.string.interception_source_app_target_app,
                                listOf(rule.targetId),
                            )
                        },
                        InterceptionSourceLine(R.string.interception_source_app_time, listOf(rule.formatTimeRange())),
                        InterceptionSourceLine(
                            R.string.interception_source_app_days,
                            listOf(rule.formatDaysOfWeek(context)),
                        ),
                        InterceptionSourceLine(
                            R.string.interception_source_app_mode,
                            listOf(rule.formatModeDescription(context)),
                        ),
                    )
                },
            )

        fun unknown(): InterceptionSourcePresentation = InterceptionSourcePresentation(
            // i18n-ignore: legacy fallback or non-display heuristic data
            title = "拦截来源",
            // i18n-ignore: legacy fallback or non-display heuristic data
            lines = listOf("本次规则信息暂不可用"),
            titleRes = R.string.interception_source_unknown_title,
            localizedLines = listOf(
                InterceptionSourceLine(R.string.interception_source_unknown_line),
            ),
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
                text = stringResource(presentation.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            presentation.localizedLines.ifEmpty {
                presentation.lines.map { InterceptionSourceLine(0, listOf(it)) }
            }.forEach { line ->
                Text(
                    text = if (line.resId != 0) {
                        stringResource(line.resId, *line.args.map { localizedArg(it) }.toTypedArray())
                    } else {
                        line.args.firstOrNull()?.toString().orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun localizedArg(arg: Any): Any = when (arg) {
    is InterceptionSourceArg -> stringResource(arg.resId, *arg.args.map { localizedArg(it) }.toTypedArray())
    else -> arg
}
