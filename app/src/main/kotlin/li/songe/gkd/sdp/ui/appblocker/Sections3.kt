@file:JvmName("AppBlockerSections32")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.AppBlockerDecisionPolicy

@Composable
@android.annotation.SuppressLint("NonObservableLocale")
internal fun AppRulesCard(
    packageName: String,
    rules: List<BlockTimeRule>,
    onToggleEnabled: (BlockTimeRule) -> Unit,
    onEdit: (BlockTimeRule) -> Unit,
    onDelete: (BlockTimeRule) -> Unit,
    onLock: (BlockTimeRule) -> Unit
) {
    val appName = remember(packageName) {
        try {
            val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

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
                AppIcon(appId = packageName)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${rules.size} 条时间规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${rules.count { it.enabled }} 条启用规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        rules.none { it.enabled } -> Text(
                            text = "尚未生效：请启用时间规则",
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // 显示规则列表
            rules.forEach { rule ->
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(rule) }
                        .padding(vertical = 8.dp)
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
                            // 显示锁定结束时间
                            val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(rule.lockEndTime))
                            Text(
                                text = "🔒 锁定至 $lockEndTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 操作按钮 - 锁定按钮始终显示（可延长锁定）
                    IconButton(onClick = { onLock(rule) }) {
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

                    // 删除按钮仅在未锁定时显示
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
                        onCheckedChange = { onToggleEnabled(rule) },
                        enabled = !rule.isCurrentlyLocked
                    )
                }

                // 删除确认对话框
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("删除规则") },
                        text = { Text("确定要删除这条时间规则吗？") },
                        confirmButton = {
                            TextButton(onClick = {
                                onDelete(rule)
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
        }
    }
}
