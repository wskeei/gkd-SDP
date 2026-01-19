package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.a11y.WechatContactFetcher
import li.songe.gkd.sdp.data.WechatContact
import li.songe.gkd.sdp.service.A11yService

@Composable
fun WechatContactPicker(
    allContacts: List<WechatContact>,
    selectedIds: List<String>,
    onContactToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val isFetching by WechatContactFetcher.isFetchingFlow.collectAsState()
    val fetchProgress by WechatContactFetcher.fetchProgressFlow.collectAsState()

    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.wechatId.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier) {
        // 顶部：更新按钮
        Button(
            onClick = {
                A11yService.instance?.let { service ->
                    WechatContactFetcher.startFetch(service)
                }
            },
            enabled = !isFetching,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isFetching) fetchProgress else "更新微信联系人")
        }

        Spacer(modifier = Modifier.padding(8.dp))

        // 搜索框
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索联系人") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.padding(8.dp))

        // 联系人列表
        if (filteredContacts.isEmpty()) {
            Text(
                text = if (allContacts.isEmpty()) "暂无联系人，请点击上方按钮更新" else "未找到匹配的联系人",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(filteredContacts, key = { it.wechatId }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactToggle(contact.wechatId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = contact.wechatId in selectedIds,
                            onCheckedChange = { onContactToggle(contact.wechatId) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = contact.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (contact.remark.isNotEmpty() && contact.remark != contact.nickname) {
                                Text(
                                    text = contact.nickname,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "微信号: ${contact.wechatId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
