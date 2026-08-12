@file:JvmName("ActionLogDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.ExcludeData
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.subsMapFlow
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun ActionLogDialog(
    actionLog: ActionLog,
    onDismissRequest: () -> Unit,
    onOpenGroup: () -> Unit,
    onToggleGlobalApp: (SubsConfig?, ExcludeData, Boolean) -> Unit,
    onTogglePage: (SubsConfig?, ExcludeData) -> Unit,
) {
    val subsConfig by remember(actionLog) {
        (if (actionLog.groupType == SubsConfig.AppGroupType) {
            DbSet.subsConfigDao.queryAppGroupTypeConfig(
                actionLog.subsId, actionLog.appId, actionLog.groupKey
            )
        } else {
            DbSet.subsConfigDao.queryGlobalGroupTypeConfig(actionLog.subsId, actionLog.groupKey)
        })
    }.collectAsStateWithLifecycle(initialValue = null)

    val oldExclude = remember(subsConfig?.exclude) {
        ExcludeData.parse(subsConfig?.exclude)
    }
    val subscriptionMap by subsMapFlow.collectAsStateWithLifecycle()
    val currentSubscription = subscriptionMap[actionLog.subsId]
    val currentGroup = currentSubscription?.let { subscription ->
        if (actionLog.groupType == SubsConfig.AppGroupType) {
            subscription.apps
                .find { app -> app.id == actionLog.appId }
                ?.groups
                ?.find { group -> group.key == actionLog.groupKey }
        } else if (actionLog.groupType == SubsConfig.GlobalGroupType) {
            subscription.globalGroups.find { group -> group.key == actionLog.groupKey }
        } else {
            null
        }
    }
    val currentRule = currentGroup?.rules?.let { rules ->
        if (actionLog.ruleKey != null) {
            rules.find { rule -> rule.key == actionLog.ruleKey }
        } else {
            rules.getOrNull(actionLog.ruleIndex)
        }
    }
    val displaySubscriptionName = presentationName(
        snapshot = actionLog.subsNameSnapshot,
        current = currentSubscription?.name,
        fallback = "id=${actionLog.subsId}",
    )
    val displayGroupName = presentationName(
        snapshot = actionLog.groupNameSnapshot,
        current = currentGroup?.name,
        fallback = stringResource(R.string.action_log_group_fallback, actionLog.groupKey),
    )
    val displayRuleName = presentationName(
        snapshot = actionLog.ruleNameSnapshot,
        current = currentRule?.name,
        fallback = actionLog.ruleKey?.let { "key=$it" } ?: "index=${actionLog.ruleIndex + 1}",
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            ItemText(
                text = stringResource(R.string.action_log_view_group),
                onClick = {
                    onDismissRequest()
                    onOpenGroup()
                }
            )
            HorizontalDivider()

            ActionLogDialogSummary(
                actionLog = actionLog,
                displaySubscriptionName = displaySubscriptionName,
                displayGroupName = displayGroupName,
                displayRuleName = displayRuleName,
            )
            HorizontalDivider()

            ActionLogDialogActions(
                actionLog = actionLog,
                oldExclude = oldExclude,
                subsConfig = subsConfig,
                onToggleGlobalApp = onToggleGlobalApp,
                onTogglePage = onTogglePage,
            )
        }
    }
}

@Composable
private fun ActionLogDialogSummary(
    actionLog: ActionLog,
    displaySubscriptionName: String,
    displayGroupName: String,
    displayRuleName: String,
) {
    val presentation = ActionLogPresentation.from(actionLog)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(presentation.outcomeTitleRes),
            style = MaterialTheme.typography.titleMedium,
            color = if (actionLog.outcome == ActionLog.OUTCOME_INTERCEPTED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = stringResource(presentation.outcomeDescriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_58d05e17f9, (displayRuleName).toString()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_4c646a2268, (displaySubscriptionName).toString(), (actionLog.subsVersion).toString()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_2b4543cfce, (displayGroupName).toString()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(R.string.s_ea63fc76c4, (actionLog.groupType).toString(), (actionLog.groupKey).toString()) +
                stringResource(R.string.action_log_rule_index, actionLog.ruleIndex) + ", " +
                (
                    actionLog.ruleKey?.let { stringResource(R.string.action_log_rule_key, it) }
                        ?: stringResource(R.string.action_log_rule_key_unset)
                    ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ActionLogDialogActions(
    actionLog: ActionLog,
    oldExclude: ExcludeData,
    subsConfig: SubsConfig?,
    onToggleGlobalApp: (SubsConfig?, ExcludeData, Boolean) -> Unit,
    onTogglePage: (SubsConfig?, ExcludeData) -> Unit,
) {
    if (actionLog.groupType == SubsConfig.GlobalGroupType) {
        val subs by subsMapFlow.collectAsStateWithLifecycle()
        val currentSubscription = subs[actionLog.subsId]
        val group = currentSubscription?.globalGroups?.find { g -> g.key == actionLog.groupKey }
        val appChecked = if (group != null) {
            getGlobalGroupChecked(currentSubscription, oldExclude, group, actionLog.appId)
        } else {
            null
        }
        if (appChecked != null) {
            ItemText(
                text = if (appChecked) {
                    stringResource(R.string.action_log_disable_this_app)
                } else {
                    stringResource(R.string.action_log_remove_app_disable)
                },
                onClick = { onToggleGlobalApp(subsConfig, oldExclude, appChecked) },
            )
            HorizontalDivider()
        }
    }

    if (actionLog.activityId != null) {
        val disabled = oldExclude.activityIds.contains(actionLog.appId to actionLog.activityId)
        ItemText(
                text = if (disabled) {
                    stringResource(R.string.action_log_remove_page_disable)
                } else {
                    stringResource(R.string.action_log_disable_this_page)
                },
                onClick = { onTogglePage(subsConfig, oldExclude) },
        )
        HorizontalDivider()
    }
}
