@file:JvmName("FocusLockDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.format

@Composable
fun MindfulPauseSheet(
    target: PauseTarget,
    onConfirm: (Boolean, Int, String) -> Unit
) {
    var enabled by remember { mutableStateOf(target.config?.enabled ?: target.initialEnabled) }
    // Cooldown is hardcoded to 10s by request
    val cooldown = 10
    var message by remember { mutableStateOf(target.config?.message ?: "这真的重要吗？") }

    val isBatch = target.groupKey == null

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = if (isBatch) "批量配置全屏拦截" else "配置全屏拦截",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = target.groupName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("启用拦截", style = MaterialTheme.typography.titleMedium)

            val switchInteractionEnabled = !target.isLocked || !enabled

            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                enabled = switchInteractionEnabled
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Message
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("沉思语录") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "说明: 触发拦截后将显示全屏提示，10秒后自动退出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onConfirm(enabled, cooldown, message) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


@Composable
@android.annotation.SuppressLint("NonObservableLocale")
fun LockDurationSheet(
    targetName: String,
    currentEndTime: Long,
    vm: FocusLockVm,
    onConfirm: () -> Unit
) {
    val isLocked = currentEndTime > System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isLocked) "延长锁定: $targetName" else "锁定: $targetName",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLocked) {
            val date = java.util.Date(currentEndTime)
            val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            Text(
                text = "当前锁定至: ${formatter.format(date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = if (isLocked) "选择要延长的时长。锁定期间规则将无法关闭。" else "锁定期间规则将无法关闭。请谨慎操作。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    480 to "8小时",
                    1440 to "1天",
                    4320 to "3天"
                )
                options.forEach { (duration, label) ->
                    TextButton(
                        onClick = {
                            vm.selectedDuration = duration
                            vm.isCustomDuration = false
                        },
                        modifier = Modifier.weight(1f),
                        border = if (!vm.isCustomDuration && vm.selectedDuration == duration)
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                    ) {
                        Text(
                            text = label,
                            color = if (!vm.isCustomDuration && vm.selectedDuration == duration)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { vm.isCustomDuration = true },
                    modifier = Modifier.width(100.dp),
                     border = if (vm.isCustomDuration)
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                ) {
                    Text(
                        text = "自定义",
                        color = if (vm.isCustomDuration)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (vm.isCustomDuration) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vm.customDaysText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    vm.customDaysText = newValue
                                }
                            },
                            label = { Text("天") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vm.customHoursText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    vm.customHoursText = newValue
                                }
                            },
                            label = { Text("小时") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLocked) "确定延长" else "确定锁定")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
