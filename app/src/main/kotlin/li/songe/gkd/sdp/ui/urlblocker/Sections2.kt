@file:JvmName("UrlBlockerSections21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon

@Composable
fun UrlInGroupRow(
    rule: UrlBlockRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name.ifBlank { rule.pattern },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!rule.isCurrentlyLocked) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    PerfIcon.Delete,
                    contentDescription = "从组中删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }

        Switch(
            checked = rule.enabled,
            onCheckedChange = { /* 内部逻辑处理 */ },
            enabled = false // 只读展示，通过 Edit 修改
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("移除规则") },
            text = { Text("确定要将此规则「${rule.pattern}」删除吗？") },
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
fun TimeRuleRow(
    rule: UrlTimeRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (rule.isAllowMode) "✓" else "🚫",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${rule.formatTimeRange()} ${rule.formatDaysOfWeek()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (rule.isAllowMode) {
                Text(
                    text = "允许时间段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (rule.isCurrentlyLocked) {
                val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(rule.lockEndTime))
                Text(
                    text = "🔒 锁定至 $lockEndTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        IconButton(onClick = onLock) {
            Icon(
                PerfIcon.Lock,
                contentDescription = if (rule.isCurrentlyLocked) "延长锁定" else "锁定",
                tint = if (rule.isCurrentlyLocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }

        if (!rule.isCurrentlyLocked) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    PerfIcon.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Switch(
            checked = rule.enabled,
            onCheckedChange = { /* 内部逻辑处理 */ },
            enabled = false // 只读展示，通过 Edit 修改
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除时间规则") },
            text = { Text("确定要删除这条时间规则吗？") },
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
