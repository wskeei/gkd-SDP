package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.share.LocalNavController
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.FocusLockUtils
import li.songe.gkd.util.throttle

@Destination
@RootGraph
@Composable
fun FocusLockPage() {
    val navController = LocalNavController.current
    val vm = viewModel<FocusLockVm>()
    val activeLock by FocusLockUtils.activeLockFlow.collectAsState()

    Scaffold(
        topBar = {
            PerfTopAppBar(navController = navController, title = "规则锁定")
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            if (activeLock != null && activeLock!!.isActive) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "当前锁定状态",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "剩余时间: ${formatRemainingTime(activeLock!!.remainingTime)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                Text("锁定时长", modifier = Modifier.titleItemPadding())
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60, 120).forEach { duration ->
                        TextButton(
                            onClick = { vm.selectedDuration = duration },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (duration < 60) "${duration}分钟" else "${duration / 60}小时",
                                color = if (vm.selectedDuration == duration)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "注意：锁定功能需要先启用规则，然后在规则列表中选择要锁定的规则组。\n当前版本暂不支持在此页面选择规则，请先在订阅页面启用规则后使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.itemPadding()
                )
            }

            item {
                Button(
                    onClick = throttle { vm.startLock() },
                    enabled = activeLock == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(if (activeLock == null) "开始锁定" else "锁定中...")
                }
            }
        }
    }
}

private fun formatRemainingTime(millis: Long): String {
    val minutes = millis / 60000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${minutes}分钟"
    }
}
