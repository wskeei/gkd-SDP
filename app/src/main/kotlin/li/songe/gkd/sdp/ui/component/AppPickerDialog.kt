package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.appInfoMapFlow
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun AppPickerDialog(
    currentApps: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    singleSelect: Boolean = false,
    excludedApps: Set<String> = emptySet(),
    titleText: String = if (singleSelect) "选择应用" else "选择应用列表",
    emptyText: String = "未找到匹配的应用",
) {
    var selectedApps by remember(currentApps) { mutableStateOf(currentApps.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()

    // 过滤应用列表
    val filteredApps = remember(appInfoMap, searchQuery, showSystemApps, excludedApps) {
        appInfoMap.values
            .filterNot { it.hidden || excludedApps.contains(it.id) }
            .filter { appInfo ->
                // 系统应用过滤
                if (!showSystemApps && appInfo.isSystem) {
                    false
                } else {
                    // 搜索过滤
                    if (searchQuery.isBlank()) {
                        true
                    } else {
                        appInfo.name.contains(searchQuery, ignoreCase = true) ||
                        appInfo.id.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.s_897fdfef89)) },
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e1dfdd0c28)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(PerfIcon.Search, contentDescription = stringResource(R.string.s_f04090805c))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(PerfIcon.Close, contentDescription = stringResource(R.string.s_7b15e5e8e7))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 系统应用开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSystemApps = !showSystemApps }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.s_52c2b4e02d),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(filteredApps) { appInfo ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (selectedApps.contains(appInfo.id)) {
                                                selectedApps - appInfo.id
                                            } else {
                                                selectedApps + appInfo.id
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = selectedApps.contains(appInfo.id),
                                    onCheckedChange = {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (it) {
                                                selectedApps + appInfo.id
                                            } else {
                                                selectedApps - appInfo.id
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIcon(appId = appInfo.id)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appInfo.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (appInfo.isSystem) {
                                        Text(
                                            text = li.songe.gkd.sdp.app.getString(R.string.s_a4be5dfa64),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedApps.toList()) }) {
                Text(stringResource(R.string.s_f526c89937))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        }
    )
}
