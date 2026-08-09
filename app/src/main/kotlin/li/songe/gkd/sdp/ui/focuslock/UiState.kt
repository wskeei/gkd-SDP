@file:JvmName("FocusLockUiState0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.ui.component.PerfIcon

data class LockTarget(
    val type: Int,
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val name: String,
    val currentEndTime: Long = 0
)


data class PauseTarget(
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val groupName: String,
    val config: InterceptConfig?,
    val isLocked: Boolean = false,
    val initialEnabled: Boolean = false
)

// --- Composable Components ---


@Composable
fun RuleItem(
    state: RuleState,
    paddingStart: androidx.compose.ui.unit.Dp,
    onLockClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.group.group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val statusText = buildString {
                if (state.isLocked) {
                    val lockSource = when (state.lockedBy) {
                        2 -> "(应用)"
                        3 -> "(订阅)"
                        else -> ""
                    }
                    append("锁定中$lockSource ")
                }
                if (state.interceptConfig?.enabled == true) {
                    append("全屏拦截")
                }
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Action Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            // Mindful Pause Button (Eco Icon)
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier.size(36.dp)
            ) {
                PerfIcon(
                    imageVector = PerfIcon.Mindful,
                    tint = if (state.interceptConfig?.enabled == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Lock Button
            IconButton(
                onClick = onLockClick,
                modifier = Modifier.size(36.dp)
            ) {
                PerfIcon(
                    imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History,
                    tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
