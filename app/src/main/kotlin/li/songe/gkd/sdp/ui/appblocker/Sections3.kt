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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

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
                        text = stringResource(R.string.s_58b598a013, (rules.size).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.s_eb20a448b7, (rules.count { it.enabled }).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        rules.none { it.enabled } -> Text(
                            text = li.songe.gkd.sdp.app.getString(R.string.s_f3cf657a3f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        rules.any {
                            it.enabled &&
                                (!AppBlockerDecisionPolicy.isValidTime(it.startTime) ||
                                    !AppBlockerDecisionPolicy.isValidTime(it.endTime) ||
                                    !AppBlockerDecisionPolicy.isValidDays(it.daysOfWeek))
                        } -> Text(
                            text = li.songe.gkd.sdp.app.getString(R.string.s_d5bc80c5db),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        rules.none { it.enabled && it.isActiveNow() } -> Text(
                            text = li.songe.gkd.sdp.app.getString(R.string.s_9f3c6b0775),
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
                                text = if (rule.isAllowMode) li.songe.gkd.sdp.app.getString(R.string.s_698a879938) else li.songe.gkd.sdp.app.getString(R.string.s_1fee3b4a52),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = li.songe.gkd.sdp.app.getString(R.string.s_e713116e5e, (rule.formatTimeRange()).toString(), (rule.formatDaysOfWeek()).toString()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (rule.isAllowMode) {
                            Text(
                                text = li.songe.gkd.sdp.app.getString(R.string.s_6d17c9576a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (rule.isCurrentlyLocked) {
                            // 显示锁定结束时间
                            val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(rule.lockEndTime))
                            Text(
                                text = li.songe.gkd.sdp.app.getString(R.string.s_f30b55361a, (lockEndTime).toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 操作按钮 - 锁定按钮始终显示（可延长锁定）
                    IconButton(onClick = { onLock(rule) }) {
                        Icon(
                            PerfIcon.Lock,
                            contentDescription = if (rule.isCurrentlyLocked) li.songe.gkd.sdp.app.getString(R.string.s_eae5fd957e) else li.songe.gkd.sdp.app.getString(R.string.s_0b707d6dcc),
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
                                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_3755f56f2f),
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
                        title = { Text(li.songe.gkd.sdp.app.getString(R.string.s_f9ad34b946)) },
                        text = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e09b9cc9e4)) },
                        confirmButton = {
                            TextButton(onClick = {
                                onDelete(rule)
                                showDeleteConfirm = false
                            }) {
                                Text(li.songe.gkd.sdp.app.getString(R.string.s_3755f56f2f))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text(li.songe.gkd.sdp.app.getString(R.string.s_4d0b4688c7))
                            }
                        }
                    )
                }
            }
        }
    }
}
