@file:JvmName("AppBlockerSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.formatDurationLocalized
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun AppBlockerPageSections(
    state: AppBlockerUiState,
    showGlobalLockSheet: Boolean,
    showGroupLockSheet: Boolean,
    showRuleLockSheet: Boolean,
    lockTargetGroup: AppGroup?,
    lockTargetRule: BlockTimeRule?,
    runtimeStatus: li.songe.gkd.sdp.a11y.SdpRuntimeFeatureCoordinator.RuntimeStatus,
    overlayPermission: Boolean,
    callbacks: AppBlockerCallbacks,
) {
    val groupEditorLocked = state.globalLock?.isCurrentlyLocked == true ||
        state.editingGroup?.isCurrentlyLocked == true
    val ruleEditorLocked = state.globalLock?.isCurrentlyLocked == true ||
        state.editingRule?.isCurrentlyLocked == true ||
        if (state.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP) {
            state.allGroups.find { it.id == state.ruleTargetId.toLongOrNull() }
                ?.isCurrentlyLocked == true
        } else {
            false
        }

    AppBlockerPageScaffold(
        state = state,
        runtimeStatus = runtimeStatus,
        overlayPermission = overlayPermission,
        callbacks = callbacks,
    )

    if (state.showGroupEditor) {
        GroupEditorSheet(
            state = state,
            callbacks = callbacks,
            isLocked = groupEditorLocked,
        )
    }

    if (state.showRuleEditor) {
        RuleEditorSheet(
            state = state,
            callbacks = callbacks,
            isLocked = ruleEditorLocked,
        )
    }

    if (showGlobalLockSheet) {
        LockSheet(
            state = state,
            callbacks = callbacks,
            title = if (state.globalLock?.isCurrentlyLocked == true) {
                stringResource(R.string.appblocker_extend_global_lock)
            } else {
                stringResource(R.string.appblocker_global_lock)
            },
            description = stringResource(R.string.appblocker_global_lock_desc),
            currentLockEndTime = state.globalLock?.lockEndTime,
            onDismiss = callbacks.onDismissGlobalLock,
            onLock = callbacks.onLockGlobal,
        )
    }

    if (showGroupLockSheet && lockTargetGroup != null) {
        val target = lockTargetGroup
        LockSheet(
            state = state,
            callbacks = callbacks,
            title = if (target.isCurrentlyLocked) {
                stringResource(R.string.appblocker_extend_lock)
            } else {
                stringResource(R.string.appblocker_lock_group)
            },
            description = stringResource(R.string.appblocker_lock_group_desc),
            currentLockEndTime = if (target.isCurrentlyLocked) target.lockEndTime else null,
            onDismiss = callbacks.onDismissGroupLock,
            onLock = callbacks.onLockGroupTarget,
        )
    }

    if (showRuleLockSheet && lockTargetRule != null) {
        val target = lockTargetRule
        LockSheet(
            state = state,
            callbacks = callbacks,
            title = if (target.isCurrentlyLocked) {
                stringResource(R.string.appblocker_extend_lock)
            } else {
                stringResource(R.string.appblocker_lock_rule)
            },
            description = stringResource(R.string.appblocker_lock_rule_desc),
            currentLockEndTime = if (target.isCurrentlyLocked) target.lockEndTime else null,
            onDismiss = callbacks.onDismissRuleLock,
            onLock = callbacks.onLockRuleTarget,
        )
    }
}

@Composable
private fun AppBlockerPageScaffold(
    state: AppBlockerUiState,
    runtimeStatus: li.songe.gkd.sdp.a11y.SdpRuntimeFeatureCoordinator.RuntimeStatus,
    overlayPermission: Boolean,
    callbacks: AppBlockerCallbacks,
) {
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = callbacks.onBack,
                    )
                },
                title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e6bbd743b3)) },
                actions = {
                    IconButton(onClick = callbacks.onOpenGlobalLock) {
                        Icon(
                            PerfIcon.Lock,
                            contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_0261a6c710),
                            tint = if (state.globalLock?.isCurrentlyLocked == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        AppBlockerPageList(
            contentPadding = padding,
            state = state,
            runtimeStatus = runtimeStatus,
            overlayPermission = overlayPermission,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun AppBlockerPageList(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    state: AppBlockerUiState,
    runtimeStatus: li.songe.gkd.sdp.a11y.SdpRuntimeFeatureCoordinator.RuntimeStatus,
    overlayPermission: Boolean,
    callbacks: AppBlockerCallbacks,
) {
    LazyColumn(modifier = Modifier.scaffoldPadding(contentPadding)) {
        item(key = "self_control_runtime_status") {
            SelfControlRuntimeStatusCard(
                runtime = runtimeStatus,
                overlayPermission = overlayPermission,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        item(key = "auto_reenable_notice") {
            AppBlockerAutoReenableNotice()
        }
        if (state.globalLock?.isCurrentlyLocked == true) {
            item(key = "global_lock_status") {
                AppBlockerGlobalLockStatus(state.globalLock)
            }
        }
        item(key = "groups_header") {
            AppBlockerSectionHeader(
                title = stringResource(R.string.appblocker_groups_count, state.allGroups.size),
                onAdd = callbacks.onOpenGroupEditor,
            )
        }
        if (state.allGroups.isEmpty()) {
            item(key = "no_groups") {
                AppBlockerEmptyText(li.songe.gkd.sdp.app.getString(R.string.appblocker_empty_group))
            }
        } else {
            items(state.allGroups, key = { "group_${it.id}" }) { group ->
                AppGroupCard(
                    group = group,
                    rules = state.allRules.filter {
                        it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                            it.targetId == group.id.toString()
                    },
                    globalLock = state.globalLock,
                    onToggleEnabled = { callbacks.onToggleGroupEnabled(group) },
                    onEdit = { callbacks.onEditGroup(group) },
                    onDelete = { callbacks.onDeleteGroup(group) },
                    onLock = { callbacks.onOpenGroupLock(group) },
                    onAddRule = { callbacks.onOpenGroupRuleEditor(group) },
                    onAddApps = { callbacks.onAddAppsToGroup(group) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item(key = "app_rules_header") {
            AppBlockerSectionHeader(
                title = stringResource(R.string.appblocker_individual_apps),
                onAdd = callbacks.onOpenAppRuleEditor,
            )
        }
        val appRules = state.allRules.filter { it.targetType == BlockTimeRule.TARGET_TYPE_APP }
        if (appRules.isEmpty()) {
            item(key = "no_app_rules") {
                AppBlockerEmptyText(li.songe.gkd.sdp.app.getString(R.string.appblocker_empty_single_rule))
            }
        } else {
            appRules.groupBy { it.targetId }.forEach { (packageName, rules) ->
                item(key = "app_$packageName") {
                    AppRulesCard(
                        packageName = packageName,
                        rules = rules,
                        onToggleEnabled = { callbacks.onToggleRuleEnabled(it) },
                        onEdit = { callbacks.onEditRule(it) },
                        onDelete = { callbacks.onDeleteRule(it) },
                        onLock = callbacks.onOpenRuleLock,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AppBlockerAutoReenableNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.s_b2d1d6afd6),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.s_ebf718dc74),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun AppBlockerGlobalLockStatus(globalLock: li.songe.gkd.sdp.data.AppBlockerLock) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PerfIcon.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.s_1640da6876),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val remainingMinutes =
                    ((globalLock.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                val remainingText = formatDurationLocalized(remainingMinutes * 60_000L)
                Text(
                    text = stringResource(R.string.s_7c36cdf41a, remainingText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun AppBlockerSectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onAdd) {
            Icon(PerfIcon.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.s_94191ce210))
        }
    }
}

@Composable
private fun AppBlockerEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.itemPadding(),
    )
}
