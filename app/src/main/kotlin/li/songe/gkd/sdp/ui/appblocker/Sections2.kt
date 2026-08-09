@file:JvmName("AppBlockerSections21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.AppBlockerDecisionPolicy

@Composable
@android.annotation.SuppressLint("NonObservableLocale")
internal fun AppGroupCard(
    group: AppGroup,
    rules: List<BlockTimeRule>,
    globalLock: AppBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddRule: () -> Unit,
    onAddApps: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppGroupCardHeader(
                group = group,
                rules = rules,
                globalLock = globalLock,
                onToggleEnabled = onToggleEnabled,
                onEdit = onEdit,
            )
            AppGroupAppList(group.getAppList())
            AppGroupRuleList(rules)
            AppGroupActions(
                group = group,
                globalLock = globalLock,
                onAddApps = onAddApps,
                onAddRule = onAddRule,
                onLock = onLock,
                onDelete = { showDeleteConfirm = true },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除应用组") },
            text = { Text("确定要删除应用组「${group.name}」吗？相关的时间规则也会被删除。") },
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
@android.annotation.SuppressLint("NonObservableLocale")
private fun AppGroupCardHeader(
    group: AppGroup,
    rules: List<BlockTimeRule>,
    globalLock: AppBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (group.isCurrentlyLocked) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        PerfIcon.Lock,
                        contentDescription = "已锁定",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = "${group.getAppList().size} 个应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (group.isCurrentlyLocked) {
                    val lockEndTime = java.text.SimpleDateFormat(
                        "MM-dd HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(group.lockEndTime))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔒 锁定至 $lockEndTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = "${rules.count { it.enabled }} 条启用时间规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppGroupStatus(group, rules)
        }
        Switch(
            checked = group.enabled,
            onCheckedChange = { onToggleEnabled() },
            enabled = !group.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true,
        )
    }
}

@Composable
private fun AppGroupStatus(group: AppGroup, rules: List<BlockTimeRule>) {
    when {
        !group.enabled -> Text(
            text = "应用组已关闭",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        rules.isEmpty() || rules.none { it.enabled } -> Text(
            text = "尚未生效：请添加时间规则",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        rules.any {
            it.enabled &&
                (!AppBlockerDecisionPolicy.isValidTime(it.startTime) ||
                    !AppBlockerDecisionPolicy.isValidTime(it.endTime) ||
                    !AppBlockerDecisionPolicy.isValidDays(it.daysOfWeek))
        } -> Text(
            text = "有时间规则格式无效，请编辑修正",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        rules.none { it.enabled && it.isActiveNow() } -> Text(
            text = "已配置，当前时段不拦截",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppGroupAppList(appList: List<String>) {
    if (appList.isEmpty()) return
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "应用列表 (${appList.size})",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        appList.forEach { packageName ->
            val appName = remember(packageName) {
                try {
                    val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                    app.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName.split(".").lastOrNull() ?: packageName
                }
            }
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(appName) },
                enabled = true,
            )
        }
    }
}

@Composable
private fun AppGroupRuleList(rules: List<BlockTimeRule>) {
    if (rules.isEmpty()) return
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "时间规则 (${rules.size})",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    rules.forEach { rule ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (rule.isAllowMode) "✓" else "🚫",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${rule.formatTimeRange()} ${rule.formatDaysOfWeek()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (rule.isAllowMode) {
                    Text(
                        text = "允许时间段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (rule.isCurrentlyLocked) {
                    Text(
                        text = "已锁定",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = {},
                enabled = false,
            )
        }
    }
}

@Composable
private fun AppGroupActions(
    group: AppGroup,
    globalLock: AppBlockerLock?,
    onAddApps: () -> Unit,
    onAddRule: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!group.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true) {
            TextButton(onClick = onAddApps) {
                Icon(PerfIcon.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加应用")
            }
            TextButton(onClick = onAddRule) {
                Icon(PerfIcon.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加规则")
            }
        }
        TextButton(onClick = onLock) {
            Icon(
                PerfIcon.Lock,
                contentDescription = null,
                tint = if (group.isCurrentlyLocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (group.isCurrentlyLocked) "延长锁定" else "锁定")
        }
        if (!group.isCurrentlyLocked) {
            TextButton(onClick = onDelete) {
                Icon(PerfIcon.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除")
            }
        }
    }
}
