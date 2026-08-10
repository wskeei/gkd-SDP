@file:JvmName("FocusLockSections32")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.format

@Composable
fun AutoReenableGuardCard(
    intervalMinutes: Int,
    changedAt: Long,
    nextEnforceAt: Long,
    dailyDisableLimit: Int,
    dailyDisableUsed: Int,
    dailyDisableDayStartAt: Long,
    onClick: () -> Unit
) {
    val autoReenableUiState = FocusLockVm.evaluateAutoReenableUiState(
        intervalMinutes = intervalMinutes,
        lastChangedAt = changedAt,
        scheduledNextEnforceAt = nextEnforceAt,
        dailyDisableLimit = dailyDisableLimit,
        dailyDisableUsed = dailyDisableUsed,
        dailyDisableDayStartAt = dailyDisableDayStartAt,
        now = System.currentTimeMillis()
    )
    val nextEditableText = if (autoReenableUiState.canEditInterval) "可立即修改" else autoReenableUiState.nextEditableAt.format("MM-dd HH:mm")
    val nextEnforceText = autoReenableUiState.nextEnforceAt.format("MM-dd HH:mm")
    val nextDailyResetText = autoReenableUiState.nextDailyResetAt.format("MM-dd HH:mm")

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "自动重开保护",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "自动重开始终启用，无法关闭；会恢复规则、使用申请开关与无障碍权限守护",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "当前间隔：$intervalMinutes 分钟（0 为高频巡检）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下次可修改：$nextEditableText",
                style = MaterialTheme.typography.bodySmall,
                color = if (!autoReenableUiState.canEditInterval) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下一次自动重开：$nextEnforceText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "今日关闭次数：${autoReenableUiState.dailyDisableUsed}/${autoReenableUiState.dailyDisableLimit}（剩余 ${autoReenableUiState.dailyDisableRemaining}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下次配额重置：$nextDailyResetText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun UrlBlockerCard(
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Block,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "网址拦截",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "已启用" else "未启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun FocusModeCard(
    isActive: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Mindful,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "专注模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isActive) "进行中" else "未启动",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun AppBlockerCard(
    enabledRuleCount: Int,
    enabledGroupCount: Int,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "应用拦截",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabledRuleCount == 0) {
                        "尚未生效：请添加时间规则"
                    } else {
                        "${enabledGroupCount} 个应用组 · $enabledRuleCount 条启用规则"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun UsageGuardCard(
    enabled: Boolean,
    scopeMode: Int,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Mindful,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用申请",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) {
                        val scopeText = if (scopeMode == 0) "仅选中应用" else "全局生效"
                        "打开受控应用前先说明原因并申请时长 · $scopeText"
                    } else {
                        "未启用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun AppInstallMonitorCard(
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Download,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "软件安装监测",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "记录分心软件安装历史",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
