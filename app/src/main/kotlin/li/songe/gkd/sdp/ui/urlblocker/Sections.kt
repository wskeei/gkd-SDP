@file:JvmName("UrlBlockerSections0")

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
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun UrlBlockerPageSections(
    state: UrlBlockerUiState,
    showGlobalLockSheet: Boolean,
    showGroupLockSheet: Boolean,
    lockTargetGroup: UrlRuleGroup?,
    showTimeRuleLockSheet: Boolean,
    lockTargetTimeRule: UrlTimeRule?,
    showUrlRuleLockSheet: Boolean,
    lockTargetUrlRule: UrlBlockRule?,
    urlEditorInitialGroupId: Long,
    urlEditorInitialTimeRule: UrlTimeRule?,
    timeRuleEditorTargetType: Int,
    timeRuleEditorTargetId: Long,
    appInfoMap: Map<String, AppInfo>,
    callbacks: UrlBlockerCallbacks,
) {
    val groupEditorLocked = state.editingGroup?.isCurrentlyLocked == true ||
        state.globalLock?.isCurrentlyLocked == true
    val editingRule = state.editingUrlRule
    val urlEditorLocked = state.globalLock?.isCurrentlyLocked == true ||
        editingRule?.isCurrentlyLocked == true ||
        (
            (editingRule?.groupId ?: 0L) > 0L &&
                state.allGroups.find { it.id == editingRule?.groupId }?.isCurrentlyLocked == true
            )
    val timeRuleEditorLocked = state.globalLock?.isCurrentlyLocked == true ||
        state.editingTimeRule?.isCurrentlyLocked == true ||
        if (timeRuleEditorTargetType == UrlTimeRule.TARGET_TYPE_RULE) {
            state.allUrlRules.find { it.id == timeRuleEditorTargetId }?.isCurrentlyLocked == true
        } else {
            state.allGroups.find { it.id == timeRuleEditorTargetId }?.isCurrentlyLocked == true
        }

    UrlBlockerScaffold(state = state, callbacks = callbacks)
    UrlBlockerEditorsAndDialogs(
        state = state,
        showGlobalLockSheet = showGlobalLockSheet,
        showGroupLockSheet = showGroupLockSheet,
        lockTargetGroup = lockTargetGroup,
        showTimeRuleLockSheet = showTimeRuleLockSheet,
        lockTargetTimeRule = lockTargetTimeRule,
        showUrlRuleLockSheet = showUrlRuleLockSheet,
        lockTargetUrlRule = lockTargetUrlRule,
        urlEditorInitialGroupId = urlEditorInitialGroupId,
        urlEditorInitialTimeRule = urlEditorInitialTimeRule,
        timeRuleEditorTargetType = timeRuleEditorTargetType,
        timeRuleEditorTargetId = timeRuleEditorTargetId,
        groupEditorLocked = groupEditorLocked,
        urlEditorLocked = urlEditorLocked,
        timeRuleEditorLocked = timeRuleEditorLocked,
        appInfoMap = appInfoMap,
        callbacks = callbacks,
    )
}

@Composable
private fun UrlBlockerScaffold(
    state: UrlBlockerUiState,
    callbacks: UrlBlockerCallbacks,
) {
    Scaffold(
        topBar = {
            UrlBlockerTopBar(state = state, callbacks = callbacks)
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item(key = "auto_reenable_notice") {
                UrlBlockerAutoReenableNotice()
            }
            if (state.globalLock?.isCurrentlyLocked == true) {
                item(key = "global_lock_status") {
                    UrlBlockerGlobalLockStatus(state = state)
                }
            }
            item(key = "groups_header") {
                UrlBlockerGroupsHeader(state = state, callbacks = callbacks)
            }
            if (state.allGroups.isEmpty()) {
                item(key = "no_groups") {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_5414239fa2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(state.allGroups, key = { "group_${it.id}" }) { group ->
                    UrlGroupCard(
                        group = group,
                        rules = state.allTimeRules.filter {
                            it.targetType == UrlTimeRule.TARGET_TYPE_GROUP &&
                                it.targetId == group.id
                        },
                        urlRules = state.allUrlRules.filter { it.groupId == group.id },
                        globalLock = state.globalLock,
                        onToggleEnabled = { callbacks.onToggleGroup(group) },
                        onEdit = { callbacks.onEditGroup(group) },
                        onDelete = { callbacks.onDeleteGroup(group) },
                        onLock = { callbacks.onLockGroup(group) },
                        onAddTimeRule = {
                            callbacks.onAddTimeRule(UrlTimeRule.TARGET_TYPE_GROUP, group.id)
                        },
                        onTimeRuleEdit = { callbacks.onEditTimeRule(it) },
                        onTimeRuleDelete = { callbacks.onDeleteTimeRule(it) },
                        onTimeRuleLock = { callbacks.onLockTimeRule(it) },
                        onAddUrlRule = { callbacks.onAddUrlRule(group.id) },
                        onEditUrlRule = { callbacks.onEditUrlRule(it) },
                        onDeleteUrlRule = { callbacks.onDeleteUrlRule(it) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item(key = "spacer_groups") {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item(key = "rules_header") {
                UrlBlockerRulesHeader(callbacks = callbacks)
            }
            val standaloneRules = state.allUrlRules.filter { it.groupId == 0L }
            if (standaloneRules.isEmpty()) {
                item(key = "no_rules") {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_30808f105b),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(standaloneRules, key = { "rule_${it.id}" }) { rule ->
                    UrlItemCard(
                        rule = rule,
                        timeRules = state.allTimeRules.filter {
                            it.targetType == UrlTimeRule.TARGET_TYPE_RULE &&
                                it.targetId == rule.id
                        },
                        globalLock = state.globalLock,
                        onToggleEnabled = { callbacks.onToggleUrlRule(rule) },
                        onEdit = { callbacks.onEditUrlRule(rule) },
                        onDelete = { callbacks.onDeleteUrlRule(rule) },
                        onAddTimeRule = {
                            callbacks.onAddTimeRule(UrlTimeRule.TARGET_TYPE_RULE, rule.id)
                        },
                        onTimeRuleEdit = { callbacks.onEditTimeRule(it) },
                        onTimeRuleDelete = { callbacks.onDeleteTimeRule(it) },
                        onTimeRuleLock = { callbacks.onLockTimeRule(it) },
                        onLock = { callbacks.onLockUrlRule(rule) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun UrlBlockerTopBar(
    state: UrlBlockerUiState,
    callbacks: UrlBlockerCallbacks,
) {
    PerfTopAppBar(
        navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = callbacks.onBack,
            )
        },
        title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_dcbbbab7a5)) },
        actions = {
            IconButton(onClick = callbacks.onOpenBrowserList) {
                Icon(
                    PerfIcon.Settings,
                    contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_362f11dc2a),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = callbacks.onOpenGlobalLock) {
                Icon(
                    PerfIcon.Lock,
                    contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_0261a6c710),
                    tint = if (state.globalLock?.isCurrentlyLocked == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
    )
}

@Composable
private fun UrlBlockerAutoReenableNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_b2d1d6afd6),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_ebf718dc74),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun UrlBlockerGlobalLockStatus(state: UrlBlockerUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(PerfIcon.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_1640da6876),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val remainingMinutes = ((state.globalLock?.lockEndTime ?: 0L) - System.currentTimeMillis()) / 60000
                Text(
                    text = if (remainingMinutes >= 60) {
                        stringResource(
                            R.string.focus_lock_hours_minutes,
                            remainingMinutes / 60,
                            remainingMinutes % 60,
                        )
                    } else {
                        stringResource(R.string.focus_lock_minutes, remainingMinutes.coerceAtLeast(0))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun UrlBlockerGroupsHeader(
    state: UrlBlockerUiState,
    callbacks: UrlBlockerCallbacks,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = li.songe.gkd.sdp.app.getString(R.string.s_bb218a940b, (state.allGroups.size).toString()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = callbacks.onAddGroup) {
            Icon(PerfIcon.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(li.songe.gkd.sdp.app.getString(R.string.s_94191ce210))
        }
    }
}

@Composable
private fun UrlBlockerRulesHeader(callbacks: UrlBlockerCallbacks) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = li.songe.gkd.sdp.app.getString(R.string.s_f6b45b5f13),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = { callbacks.onAddUrlRule(0L) }) {
            Icon(PerfIcon.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(li.songe.gkd.sdp.app.getString(R.string.s_94191ce210))
        }
    }
}

@Composable
private fun UrlBlockerEditorsAndDialogs(
    state: UrlBlockerUiState,
    showGlobalLockSheet: Boolean,
    showGroupLockSheet: Boolean,
    lockTargetGroup: UrlRuleGroup?,
    showTimeRuleLockSheet: Boolean,
    lockTargetTimeRule: UrlTimeRule?,
    showUrlRuleLockSheet: Boolean,
    lockTargetUrlRule: UrlBlockRule?,
    urlEditorInitialGroupId: Long,
    urlEditorInitialTimeRule: UrlTimeRule?,
    timeRuleEditorTargetType: Int,
    timeRuleEditorTargetId: Long,
    groupEditorLocked: Boolean,
    urlEditorLocked: Boolean,
    timeRuleEditorLocked: Boolean,
    appInfoMap: Map<String, AppInfo>,
    callbacks: UrlBlockerCallbacks,
) {
    if (state.showGroupEditor) {
        UrlGroupEditorSheet(
            editingGroup = state.editingGroup,
            isLocked = groupEditorLocked,
            onDismiss = callbacks.onDismissGroupEditor,
            onSave = callbacks.onSaveGroup,
        )
    }

    if (state.showUrlEditor) {
        UrlRuleEditorSheet(
            editingRule = state.editingUrlRule,
            allGroups = state.allGroups,
            initialGroupId = urlEditorInitialGroupId,
            initialTimeRule = urlEditorInitialTimeRule,
            isLocked = urlEditorLocked,
            onDismiss = callbacks.onDismissUrlEditor,
            onSave = callbacks.onSaveUrlRule,
        )
    }

    if (state.showTimeRuleEditor) {
        TimeRuleEditorSheet(
            editingRule = state.editingTimeRule,
            targetType = timeRuleEditorTargetType,
            targetId = timeRuleEditorTargetId,
            isLocked = timeRuleEditorLocked,
            onDismiss = callbacks.onDismissTimeRuleEditor,
            onSave = callbacks.onSaveTimeRule,
        )
    }

    if (state.showBrowserList) {
        BrowserListSheet(
            browsers = state.browsers,
            onDismiss = callbacks.onDismissBrowserList,
            onAdd = callbacks.onAddBrowser,
            onEdit = callbacks.onEditBrowser,
            onDelete = callbacks.onDeleteBrowser,
            onToggle = callbacks.onToggleBrowser,
        )
    }

    if (state.showBrowserEditor) {
        BrowserEditSheet(
            editingBrowser = state.editingBrowser,
            appInfoMap = appInfoMap,
            onDismiss = callbacks.onDismissBrowserEditor,
            onSave = callbacks.onSaveBrowser,
        )
    }

    if (showGlobalLockSheet) {
        UrlLockSheet(
            title = if (state.globalLock?.isCurrentlyLocked == true) {
                stringResource(R.string.s_a04aff06d0)
            } else {
                stringResource(R.string.s_0261a6c710)
            },
            description = stringResource(R.string.s_63c9be57f7),
            currentLockEndTime = state.globalLock?.lockEndTime,
            onDismiss = callbacks.onDismissGlobalLock,
            onLock = callbacks.onLockGlobal,
        )
    }

    if (showGroupLockSheet && lockTargetGroup != null) {
        UrlLockSheet(
            title = if (lockTargetGroup.isCurrentlyLocked) {
                stringResource(R.string.s_eae5fd957e)
            } else {
                stringResource(R.string.s_b9a0e4b4ab)
            },
            description = stringResource(R.string.s_bbacb690bd),
            currentLockEndTime = if (lockTargetGroup.isCurrentlyLocked) lockTargetGroup.lockEndTime else null,
            onDismiss = callbacks.onDismissGroupLock,
            onLock = callbacks.onLockGroupTarget,
        )
    }

    if (showTimeRuleLockSheet && lockTargetTimeRule != null) {
        UrlLockSheet(
            title = if (lockTargetTimeRule.isCurrentlyLocked) {
                stringResource(R.string.s_eae5fd957e)
            } else {
                stringResource(R.string.s_744ffb01f4)
            },
            description = stringResource(R.string.s_d21804ab1a),
            currentLockEndTime = if (lockTargetTimeRule.isCurrentlyLocked) {
                lockTargetTimeRule.lockEndTime
            } else {
                null
            },
            onDismiss = callbacks.onDismissTimeRuleLock,
            onLock = callbacks.onLockTimeRuleTarget,
        )
    }

    if (showUrlRuleLockSheet && lockTargetUrlRule != null) {
        UrlLockSheet(
            title = if (lockTargetUrlRule.isCurrentlyLocked) {
                stringResource(R.string.s_eae5fd957e)
            } else {
                stringResource(R.string.s_2201b864c9)
            },
            description = stringResource(R.string.s_c9a939b41d),
            currentLockEndTime = if (lockTargetUrlRule.isCurrentlyLocked) {
                lockTargetUrlRule.lockEndTime
            } else {
                null
            },
            onDismiss = callbacks.onDismissUrlRuleLock,
            onLock = callbacks.onLockUrlRuleTarget,
        )
    }
}
