@file:JvmName("UrlBlockerSections0")

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
            title = { Text("删除规则组") },
            text = { Text("确定要删除规则组「${group.name}」吗？组内的所有规则也会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
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
                        Icon(PerfIcon.Lock, contentDescription = "已锁定", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (group.isCurrentlyLocked) {
                    val lockEndLocale = LocalConfiguration.current.locales[0]
                    val lockEndTime = remember(lockEndLocale, group.lockEndTime) {
                        java.text.SimpleDateFormat("MM-dd HH:mm", lockEndLocale)
                            .format(java.util.Date(group.lockEndTime))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🔒 锁定至 $lockEndTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
            Text("包含的网址 (${urlRules.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            urlRules.forEach { urlRule ->
                UrlInGroupRow(rule = urlRule, onEdit = { onEditUrlRule(urlRule) }, onDelete = { onDeleteUrlRule(urlRule) })
            }
        }
        if (rules.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("时间规则 (${rules.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            rules.forEach { rule ->
                TimeRuleRow(rule = rule, onEdit = { onTimeRuleEdit(rule) }, onDelete = { onTimeRuleDelete(rule) }, onLock = { onTimeRuleLock(rule) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (!group.isCurrentlyLocked) {
                TextButton(onClick = onAddUrlRule) { Icon(PerfIcon.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("网址") }
                TextButton(onClick = onAddTimeRule) { Icon(PerfIcon.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("时间") }
            }
            TextButton(onClick = onLock) {
                Icon(PerfIcon.Lock, contentDescription = null, tint = if (group.isCurrentlyLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (group.isCurrentlyLocked) "延长锁定" else "锁定")
            }
            if (!group.isCurrentlyLocked) {
                TextButton(onClick = onDelete) { Icon(PerfIcon.Delete, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("删除") }
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
            // 规则头
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
                                contentDescription = "已锁定",
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
                            text = "🔒 锁定至 $lockEndTime",
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

            // 时间规则列表
            if (timeRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "时间规则 (${timeRules.size})",
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

            // 操作按钮
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
                        Text("时间规则")
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
                    Text(if (rule.isCurrentlyLocked) "延长锁定" else "锁定")
                }

                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(PerfIcon.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除规则") },
            text = { Text("确定要删除这条规则吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
