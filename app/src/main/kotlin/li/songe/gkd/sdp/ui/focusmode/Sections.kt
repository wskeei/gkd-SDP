@file:JvmName("FocusModeSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun FocusModePageSections(
    state: FocusModeUiState,
    showQuickStartSheet: Boolean,
    showRuleEditorSheet: Boolean,
    showWhitelistPicker: Boolean,
    showLockSheet: Boolean,
    lockTargetRule: FocusRule?,
    whitelistPickerMode: String,
    callbacks: FocusModeCallbacks,
) {
    val quickStartRules = remember(state.allRules) { state.allRules.filter { it.isQuickStart } }
    val scheduledRules = remember(state.allRules) { state.allRules.filterNot { it.isQuickStart } }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = callbacks.onBack,
                    )
                },
                title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_63c1371c03)) },
                actions = {
                    IconButton(onClick = callbacks.onOpenRuleEditor) {
                        Icon(PerfIcon.Add, contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_d2fc32282a))
                    }
                }
            )
        }
    ) { padding ->
        FocusModeRulesList(
            padding = padding,
            state = state,
            quickStartRules = quickStartRules,
            scheduledRules = scheduledRules,
            callbacks = callbacks,
        )
    }

    FocusModeSheets(
        state = state,
        showQuickStartSheet = showQuickStartSheet,
        showRuleEditorSheet = showRuleEditorSheet,
        showWhitelistPicker = showWhitelistPicker,
        showLockSheet = showLockSheet,
        lockTargetRule = lockTargetRule,
        whitelistPickerMode = whitelistPickerMode,
        callbacks = callbacks,
    )
}

@Composable
private fun FocusModeRulesList(
    padding: androidx.compose.foundation.layout.PaddingValues,
    state: FocusModeUiState,
    quickStartRules: List<FocusRule>,
    scheduledRules: List<FocusRule>,
    callbacks: FocusModeCallbacks,
) {
    LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
        item(key = "status") {
            ActiveSessionCard(
                session = state.activeSession,
                isActive = state.isActive,
                currentWhitelist = state.currentWhitelist,
                onStop = callbacks.onStopManualSession,
                onRemoveWhitelist = callbacks.onRemoveFromSessionWhitelist,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (!state.isActive) {
            item(key = "quick_start") {
                Button(onClick = callbacks.onOpenQuickStart, modifier = Modifier.fillMaxWidth().itemPadding()) { Text(li.songe.gkd.sdp.app.getString(R.string.s_eb4f824680)) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        item(key = "quick_rules_header") {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_fa25aa2cc7), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.itemPadding())
            Spacer(modifier = Modifier.height(8.dp))
        }
        FocusModeRuleItems(
            rules = quickStartRules,
            emptyTextRes = R.string.focus_mode_empty_quick_start,
            callbacks = callbacks,
        )
        item(key = "scheduled_rules_header") {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_a497f76289), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.itemPadding())
            Spacer(modifier = Modifier.height(8.dp))
        }
        FocusModeRuleItems(
            rules = scheduledRules,
            emptyTextRes = R.string.focus_mode_empty_scheduled,
            callbacks = callbacks,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.FocusModeRuleItems(
    rules: List<FocusRule>,
    emptyTextRes: Int,
    callbacks: FocusModeCallbacks,
) {
    if (rules.isEmpty()) {
        item {
            Text(
                stringResource(emptyTextRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.itemPadding(),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        items(rules, key = { "rule_${it.id}" }) { rule ->
            FocusRuleCard(
                rule = rule,
                onToggleEnabled = { callbacks.onToggleRuleEnabled(rule) },
                onEdit = { callbacks.onEditRule(rule) },
                onDelete = { callbacks.onDeleteRule(rule) },
                onLock = { callbacks.onOpenRuleLock(rule) },
                onStart = { callbacks.onStartQuickRule(rule) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FocusModeSheets(
    state: FocusModeUiState,
    showQuickStartSheet: Boolean,
    showRuleEditorSheet: Boolean,
    showWhitelistPicker: Boolean,
    showLockSheet: Boolean,
    lockTargetRule: FocusRule?,
    whitelistPickerMode: String,
    callbacks: FocusModeCallbacks,
) {
    if (showQuickStartSheet) {
        QuickStartSheet(
            state = state,
            callbacks = callbacks,
        )
    }
    if (showRuleEditorSheet || state.showRuleEditor) {
        RuleEditorSheet(
            state = state,
            callbacks = callbacks,
        )
    }
    if (showWhitelistPicker) {
        WhitelistPickerDialog(
            currentWhitelist = if (whitelistPickerMode == "rule") {
                state.ruleWhitelistApps
            } else {
                state.manualWhitelistApps
            },
            state = state,
            callbacks = callbacks,
        )
    }
    if (showLockSheet && lockTargetRule != null) {
        LockRuleSheet(
            state = state,
            callbacks = callbacks,
            rule = lockTargetRule,
        )
    }
}


@Composable
internal fun ActiveSessionCard(
    session: FocusSession?,
    isActive: Boolean,
    currentWhitelist: List<String>,
    onStop: () -> Unit,
    onRemoveWhitelist: (String) -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    PerfIcon.Mindful,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) stringResource(R.string.s_7c2c7f64bf) else stringResource(R.string.s_175813e4ff),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive && session?.isValidNow() == true) {
                        val remainingMinutes = session.getRemainingTime() / 60000
                        Text(
                            text = if (remainingMinutes >= 60) {
                                stringResource(R.string.s_517526331f, (remainingMinutes / 60).toString(), (remainingMinutes % 60).toString())
                            } else {
                                stringResource(R.string.s_ec9af249f1, (remainingMinutes).toString())
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (session.isCurrentlyLocked) {
                            Text(
                                text = stringResource(R.string.s_491a7f5bd7),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                if (isActive && session?.isManual == true && !session.isCurrentlyLocked) {
                    OutlinedButton(onClick = onStop) {
                        Text(stringResource(R.string.s_76b9880829))
                    }
                }
            }

            if (isActive && currentWhitelist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.s_0f8458ec0b),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                currentWhitelist.forEach { packageName ->
                    WhitelistAppRow(
                        packageName = packageName,
                        canRemove = session?.isCurrentlyLocked != true,
                        onRemove = { onRemoveWhitelist(packageName) }
                    )
                }
            }
        }
    }
}


@Composable
internal fun WhitelistAppRow(
    packageName: String,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val appName = remember(packageName) {
        try {
            val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        AppIcon(appId = packageName)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(PerfIcon.Close, contentDescription = stringResource(R.string.s_2f752c005e))
            }
        }
    }
}
