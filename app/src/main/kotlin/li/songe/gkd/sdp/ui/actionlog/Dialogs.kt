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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.ExcludeData
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.subsMapFlow
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Composable
internal fun ActionLogDialog(
    vm: ViewModel,
    actionLog: ActionLog,
    onDismissRequest: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    val subsConfig = remember(actionLog) {
        (if (actionLog.groupType == SubsConfig.AppGroupType) {
            DbSet.subsConfigDao.queryAppGroupTypeConfig(
                actionLog.subsId, actionLog.appId, actionLog.groupKey
            )
        } else {
            DbSet.subsConfigDao.queryGlobalGroupTypeConfig(actionLog.subsId, actionLog.groupKey)
        }).stateIn(vm.viewModelScope, SharingStarted.Eagerly, null)
    }.collectAsStateWithLifecycle().value

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
        fallback = "规则组 ${actionLog.groupKey}",
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
                text = stringResource(R.string.s_451bb58ff2),
                onClick = {
                    onDismissRequest()
                    if (actionLog.groupType == SubsConfig.AppGroupType) {
                        mainVm.navigatePage(
                            SubsAppGroupListRoute(
                                actionLog.subsId, actionLog.appId, actionLog.groupKey
                            )
                        )
                    } else if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                        mainVm.navigatePage(
                            SubsGlobalGroupListRoute(
                                actionLog.subsId, actionLog.groupKey
                            )
                        )
                    }
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
                vm = vm,
                scope = scope,
                actionLog = actionLog,
                oldExclude = oldExclude,
                subsConfig = subsConfig,
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
            text = presentation.outcomeTitle,
            style = MaterialTheme.typography.titleMedium,
            color = if (actionLog.outcome == ActionLog.OUTCOME_INTERCEPTED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = presentation.outcomeDescription,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_58d05e17f9, displayRuleName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_4c646a2268, displaySubscriptionName, actionLog.subsVersion),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.s_2b4543cfce, displayGroupName),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(R.string.s_2e56559e95, actionLog.groupType, actionLog.groupKey, actionLog.ruleIndex, actionLog.ruleKey?.let { "key=$it" } ?:)未设置 key"}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ActionLogDialogActions(
    vm: ViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    actionLog: ActionLog,
    oldExclude: ExcludeData,
    subsConfig: SubsConfig?,
) {
    if (actionLog.groupType == SubsConfig.GlobalGroupType) {
        val subs = remember(actionLog.subsId) {
            subsMapFlow.mapState(scope) { it[actionLog.subsId] }
        }.collectAsStateWithLifecycle().value
        val group = subs?.globalGroups?.find { g -> g.key == actionLog.groupKey }
        val appChecked = if (group != null) {
            getGlobalGroupChecked(subs, oldExclude, group, actionLog.appId)
        } else {
            null
        }
        if (appChecked != null) {
            ItemText(
                text = if (appChecked) stringResource(R.string.s_cda4925583) else stringResource(R.string.s_d898e0730a),
                onClick = vm.viewModelScope.launchAsFn {
                    val effectiveConfig = subsConfig ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsId = actionLog.subsId,
                        groupKey = actionLog.groupKey,
                    )
                    DbSet.subsConfigDao.insert(
                        effectiveConfig.copy(
                            exclude = oldExclude.copy(
                                appIds = oldExclude.appIds.toMutableMap().apply {
                                    set(actionLog.appId, appChecked)
                                },
                            ).stringify(),
                        ),
                    )
                    toast(app.getString(R.string.s_e2cff77372))
                },
            )
            HorizontalDivider()
        }
    }

    if (actionLog.activityId != null) {
        val disabled = oldExclude.activityIds.contains(actionLog.appId to actionLog.activityId)
        ItemText(
            text = if (disabled) stringResource(R.string.s_c9be3b4423) else stringResource(R.string.s_d66870c055),
            onClick = vm.viewModelScope.launchAsFn {
                val effectiveConfig = if (actionLog.groupType == SubsConfig.AppGroupType) {
                    subsConfig ?: SubsConfig(
                        type = SubsConfig.AppGroupType,
                        subsId = actionLog.subsId,
                        appId = actionLog.appId,
                        groupKey = actionLog.groupKey,
                    )
                } else {
                    subsConfig ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsId = actionLog.subsId,
                        groupKey = actionLog.groupKey,
                    )
                }
                DbSet.subsConfigDao.insert(
                    effectiveConfig.copy(
                        exclude = oldExclude.switch(actionLog.appId, actionLog.activityId).stringify(),
                    ),
                )
                toast(app.getString(R.string.s_e2cff77372))
            },
        )
        HorizontalDivider()
    }
}
