@file:JvmName("ActionLogSections21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.ui.component.AppNameText
import li.songe.gkd.sdp.ui.component.FixedTimeText
import li.songe.gkd.sdp.ui.component.GroupNameText
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.subsMapFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun ActionLogCard(
    modifier: Modifier = Modifier,
    i: Int,
    item: Triple<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>,
    lastItem: Triple<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>?,
    onClick: () -> Unit,
    subsId: Long?,
    appId: String?,
) {
    val mainVm = LocalMainViewModel.current
    val (actionLog, group, rule) = item
    val lastActionLog = lastItem?.first
    val isDiffApp = actionLog.appId != lastActionLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val subsIdToRaw by subsMapFlow.collectAsStateWithLifecycle()
    val subscription = subsIdToRaw[actionLog.subsId]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding / 2,
                end = itemHorizontalPadding / 2,
                top = verticalPadding
            )
    ) {
        if (isDiffApp && appId == null) {
            Row(
                modifier = Modifier
                    .padding(start = itemHorizontalPadding / 4)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = throttle {
                        mainVm.navigatePage(
                            AppConfigRoute(
                                appId = actionLog.appId,
                            )
                        )
                    })
                    .fillMaxWidth()
                    .padding(start = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    Spacer(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                            .size(4.dp)
                    )
                    AppNameText(appId = actionLog.appId, modifier = Modifier.weight(1f))
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
                        modifier = Modifier
                            .iconTextSize()
                    )
                }
            }
        }
        ActionLogCardBody(
            actionLog = actionLog,
            group = group,
            rule = rule,
            item = item,
            subscription = subscription,
            subsId = subsId,
            appId = appId,
            onClick = onClick,
        )
    }
}

@Composable
private fun ActionLogCardBody(
    actionLog: ActionLog,
    group: RawSubscription.RawGroupProps?,
    rule: RawSubscription.RawRuleProps?,
    item: Triple<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>,
    subscription: RawSubscription?,
    subsId: Long?,
    appId: String?,
    onClick: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    Row(
        modifier = Modifier
            .padding(start = itemHorizontalPadding / 4)
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = itemHorizontalPadding / 4)
    ) {
            if (appId == null) {
                Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                FixedTimeText(
                    text = actionLog.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                val outcomePresentation = ActionLogPresentation.from(actionLog)
                Text(
                    text = stringResource(R.string.s_6027bb300c, (outcomePresentation.outcomeTitle).toString(), (outcomePresentation.outcomeDescription).toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (actionLog.outcome == ActionLog.OUTCOME_INTERCEPTED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    val showActivityId = actionLog.showActivityId
                    if (showActivityId != null) {
                        Text(
                            text = showActivityId,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    } else {
                        Text(
                            text = li.songe.gkd.sdp.app.getString(R.string.s_2be88ca424),
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    if (subsId == null) {
                        Row(
                            modifier = Modifier.clickable(onClick = throttle {
                                if (subsItemsFlow.value.any { it.id == actionLog.subsId }) {
                                    mainVm.sheetSubsIdFlow.value = actionLog.subsId
                                } else {
                                    toast(li.songe.gkd.sdp.app.getString(R.string.s_9e5cc3140b))
                                }
                            })
                        ) {
                            Text(
                                text = actionLog.subsNameSnapshot
                                    ?: subscription?.name
                                    ?: li.songe.gkd.sdp.app.getString(R.string.s_fbc86835e2, (actionLog.subsId).toString())
                            )
                            val lineHeightDp = LocalDensity.current.run {
                                LocalTextStyle.current.lineHeight.toDp()
                            }
                            Row(
                                modifier = Modifier
                                    .height(lineHeightDp)
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = li.songe.gkd.sdp.app.getString(R.string.s_f498bf1ba7, (item.first.subsVersion).toString()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val groupDesc = actionLog.groupNameSnapshot
                            ?: group?.name
                            ?: "规则组 ${actionLog.groupKey}"
                        val textColor = LocalContentColor.current.let {
                            if (group == null && actionLog.groupNameSnapshot == null) it.copy(alpha = 0.5f) else it
                        }
                        GroupNameText(
                            isGlobal = actionLog.groupType == SubsConfig.GlobalGroupType,
                            text = groupDesc,
                            color = textColor,
                        )
                        val ruleDesc = actionLog.ruleNameSnapshot
                            ?: rule?.name
                            ?: (if ((group?.rules?.size ?: 0) > 1) {
                            val keyDesc = actionLog.ruleKey?.let { "key=$it, " } ?: ""
                            "${keyDesc}index=${actionLog.ruleIndex + 1}"
                        } else {
                            null
                        })
                        if (ruleDesc != null) {
                            Text(
                                text = ruleDesc,
                                modifier = Modifier.padding(start = 8.dp),
                                color = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
    }
}


internal fun presentationName(
    snapshot: String?,
    current: String?,
    fallback: String,
): String = snapshot?.trim().takeUnless { it.isNullOrEmpty() }
    ?: current?.trim().takeUnless { it.isNullOrEmpty() }
    ?: fallback


@Composable
fun ItemText(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .clickable(onClick = throttle(onClick))
        .fillMaxWidth()
        .padding(16.dp)
    Text(
        modifier = modifier,
        text = text,
        color = color,
    )
}
