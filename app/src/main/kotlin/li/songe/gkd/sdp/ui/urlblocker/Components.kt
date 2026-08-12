@file:JvmName("UrlBlockerComponents")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun UrlGroupCard(
    group: UrlRuleGroup,
    rules: List<UrlTimeRule>,
    urlRules: List<UrlBlockRule>,
    globalLock: UrlBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddTimeRule: () -> Unit,
    onTimeRuleEdit: (UrlTimeRule) -> Unit,
    onTimeRuleDelete: (UrlTimeRule) -> Unit,
    onTimeRuleLock: (UrlTimeRule) -> Unit,
    onAddUrlRule: () -> Unit,
    onEditUrlRule: (UrlBlockRule) -> Unit,
    onDeleteUrlRule: (UrlBlockRule) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        UrlGroupCardBody(
            group = group,
            rules = rules,
            urlRules = urlRules,
            globalLock = globalLock,
            onToggleEnabled = onToggleEnabled,
            onEdit = onEdit,
            onAddTimeRule = onAddTimeRule,
            onTimeRuleEdit = onTimeRuleEdit,
            onTimeRuleDelete = onTimeRuleDelete,
            onTimeRuleLock = onTimeRuleLock,
            onAddUrlRule = onAddUrlRule,
            onEditUrlRule = onEditUrlRule,
            onDeleteUrlRule = onDeleteUrlRule,
            onLock = onLock,
            onDelete = { showDeleteConfirm = true },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.s_2d646aa66b)) },
            text = { Text(stringResource(R.string.s_0ce42bb010, (group.name).toString())) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.s_3755f56f2f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.s_4d0b4688c7))
                }
            }
        )
    }
}

@Composable
private fun UrlGroupCardBody(
    group: UrlRuleGroup,
    rules: List<UrlTimeRule>,
    urlRules: List<UrlBlockRule>,
    globalLock: UrlBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onAddTimeRule: () -> Unit,
    onTimeRuleEdit: (UrlTimeRule) -> Unit,
    onTimeRuleDelete: (UrlTimeRule) -> Unit,
    onTimeRuleLock: (UrlTimeRule) -> Unit,
    onAddUrlRule: () -> Unit,
    onEditUrlRule: (UrlBlockRule) -> Unit,
    onDeleteUrlRule: (UrlBlockRule) -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (group.isCurrentlyLocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(PerfIcon.Lock, contentDescription = stringResource(R.string.s_3cc7a5af4c), tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (group.isCurrentlyLocked) {
                    val lockEndLocale = LocalConfiguration.current.locales[0]
                    val lockEndTime = remember(lockEndLocale, group.lockEndTime) {
                        java.text.SimpleDateFormat("MM-dd HH:mm", lockEndLocale)
                            .format(java.util.Date(group.lockEndTime))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.s_f30b55361a, (lockEndTime).toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Switch(
                checked = group.enabled,
                onCheckedChange = { onToggleEnabled() },
                enabled = !group.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true,
            )
        }
        if (urlRules.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.s_70fa42d276, (urlRules.size).toString()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            urlRules.forEach { urlRule ->
                UrlInGroupRow(rule = urlRule, onEdit = { onEditUrlRule(urlRule) }, onDelete = { onDeleteUrlRule(urlRule) })
            }
        }
        if (rules.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.s_68fd9e7f42, (rules.size).toString()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            rules.forEach { rule ->
                TimeRuleRow(rule = rule, onEdit = { onTimeRuleEdit(rule) }, onDelete = { onTimeRuleDelete(rule) }, onLock = { onTimeRuleLock(rule) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (!group.isCurrentlyLocked) {
                TextButton(onClick = onAddUrlRule) { Icon(PerfIcon.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text(stringResource(R.string.s_33fa921b9f)) }
                TextButton(onClick = onAddTimeRule) { Icon(PerfIcon.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text(stringResource(R.string.s_89b4aa6364)) }
            }
            TextButton(onClick = onLock) {
                Icon(PerfIcon.Lock, contentDescription = null, tint = if (group.isCurrentlyLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (group.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_0b707d6dcc))
            }
            if (!group.isCurrentlyLocked) {
                TextButton(onClick = onDelete) { Icon(PerfIcon.Delete, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text(stringResource(R.string.s_3755f56f2f)) }
            }
        }
    }
}

@Composable
fun UrlItemCard(
    rule: UrlBlockRule,
    timeRules: List<UrlTimeRule>,
    globalLock: UrlBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddTimeRule: () -> Unit,
    onTimeRuleEdit: (UrlTimeRule) -> Unit,
    onTimeRuleDelete: (UrlTimeRule) -> Unit,
    onTimeRuleLock: (UrlTimeRule) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name.ifBlank { rule.pattern },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (rule.isCurrentlyLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                PerfIcon.Lock,
                                contentDescription = stringResource(R.string.s_3cc7a5af4c),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        text = rule.pattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.isCurrentlyLocked) {
                        val lockEndLocale = LocalConfiguration.current.locales[0]
                        val lockEndTime = remember(lockEndLocale, rule.lockEndTime) {
                            java.text.SimpleDateFormat("MM-dd HH:mm", lockEndLocale)
                                .format(java.util.Date(rule.lockEndTime))
                        }
                        Text(
                            text = stringResource(R.string.s_f30b55361a, (lockEndTime).toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = !rule.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true
                )
            }

            if (timeRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.s_c6191f388a, (timeRules.size).toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                timeRules.forEach { tr ->
                    TimeRuleRow(
                        rule = tr,
                        onEdit = { onTimeRuleEdit(tr) },
                        onDelete = { onTimeRuleDelete(tr) },
                        onLock = { onTimeRuleLock(tr) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = onAddTimeRule) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.s_8102e48aad))
                    }
                }

                TextButton(onClick = onLock) {
                    Icon(
                        PerfIcon.Lock,
                        contentDescription = null,
                        tint = if (rule.isCurrentlyLocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (rule.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_0b707d6dcc))
                }

                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(PerfIcon.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.s_3755f56f2f))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.s_f9ad34b946)) },
            text = { Text(stringResource(R.string.s_8e2d76bc89)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.s_3755f56f2f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.s_4d0b4688c7))
                }
            }
        )
    }
}
