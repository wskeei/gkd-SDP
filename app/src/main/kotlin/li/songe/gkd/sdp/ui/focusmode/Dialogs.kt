@file:JvmName("FocusModeDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.util.appInfoMapFlow
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickStartSheet(
    state: FocusModeUiState,
    callbacks: FocusModeCallbacks,
) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismissQuickStart,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.s_eb4f824680),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 时长选择
            Text(
                text = stringResource(R.string.s_427069f0a2),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.manualHours.toString(),
                    onValueChange = {
                        val hours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
                        callbacks.onManualHoursChange(hours)
                    },
                    label = { Text(stringResource(R.string.s_99f6904ff3)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.manualMinutes.toString(),
                    onValueChange = {
                        val minutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        callbacks.onManualMinutesChange(minutes)
                    },
                    label = { Text(stringResource(R.string.s_28bf227b9b)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // 显示验证提示
            if (state.manualTotalDurationMinutes < 5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.s_09c309db65),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 拦截消息
            OutlinedTextField(
                value = state.manualMessage,
                onValueChange = callbacks.onManualMessageChange,
                label = { Text(stringResource(R.string.s_f82dffbf08)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 白名单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.s_d6447fb450, (state.manualWhitelistApps.size).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = callbacks.onOpenManualWhitelistPicker) {
                    Text(stringResource(R.string.s_70b208202c))
                }
            }

            if (state.manualWhitelistApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.manualWhitelistApps.forEach { packageName ->
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
                            onClick = {
                                callbacks.onManualWhitelistChange(
                                    state.manualWhitelistApps - packageName,
                                )
                            },
                            label = { Text(appName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 锁定选项
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = state.manualIsLocked,
                    onCheckedChange = callbacks.onManualIsLockedChange,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.s_9c66857925))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = callbacks.onStartManualSession,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.s_a273727311))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
internal fun WhitelistPickerDialog(
    currentWhitelist: List<String>,
    state: FocusModeUiState,
    callbacks: FocusModeCallbacks,
) {
    var selectedApps by remember { mutableStateOf(currentWhitelist.toSet()) }
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = callbacks.onDismissWhitelistPicker,
        title = { Text(stringResource(R.string.s_a63ec9e8f8)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = state.whitelistSearchQuery,
                    onValueChange = callbacks.onWhitelistSearchQueryChange,
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_897fdfef89)) },
                    leadingIcon = { Icon(PerfIcon.Search, null) },
                    trailingIcon = {
                        if (state.whitelistSearchQuery.isNotEmpty()) {
                            IconButton(onClick = callbacks.onClearWhitelistSearchQuery) {
                                Icon(PerfIcon.Close, li.songe.gkd.sdp.app.getString(R.string.s_7b15e5e8e7))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 系统应用开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            callbacks.onShowSystemAppsInWhitelistChange(
                                !state.showSystemAppsInWhitelist,
                            )
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.s_52c2b4e02d))
                    Switch(
                        checked = state.showSystemAppsInWhitelist,
                        onCheckedChange = callbacks.onShowSystemAppsInWhitelistChange,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用列表
                LazyColumn {
                    items(
                        appInfoMap.values
                            .filterNot { !state.showSystemAppsInWhitelist && it.isSystem }
                            .filter { appInfo ->
                                if (state.whitelistSearchQuery.isBlank()) {
                                    !appInfo.hidden
                                } else {
                                    !appInfo.hidden && (
                                        appInfo.name.contains(state.whitelistSearchQuery, ignoreCase = true) ||
                                        appInfo.id.contains(state.whitelistSearchQuery, ignoreCase = true)
                                    )
                                }
                            }
                            .sortedBy { it.name }
                            .toList()
                    ) { appInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApps = if (selectedApps.contains(appInfo.id)) {
                                        selectedApps - appInfo.id
                                    } else {
                                        selectedApps + appInfo.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedApps.contains(appInfo.id),
                                onCheckedChange = {
                                    selectedApps = if (it) {
                                        selectedApps + appInfo.id
                                    } else {
                                        selectedApps - appInfo.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppIcon(appId = appInfo.id)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = appInfo.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appInfo.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { callbacks.onWhitelistConfirm(selectedApps.toList()) }) {
                Text(stringResource(R.string.s_f526c89937))
            }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onDismissWhitelistPicker) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        }
    )
}
