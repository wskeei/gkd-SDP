@file:JvmName("FocusLockComponents")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.component.PerfIcon
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun RuleItem(
    state: RuleState,
    paddingStart: Dp,
    onLockClick: () -> Unit,
    onPauseClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.group.group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val lockApp = stringResource(R.string.focus_lock_source_app)
            val lockSubscription = stringResource(R.string.focus_lock_source_subscription)
            val fullscreenIntercept = stringResource(R.string.focus_lock_fullscreen)
            val statusText = buildString {
                if (state.isLocked) {
                    val lockSource = when (state.lockedBy) {
                        2 -> lockApp
                        3 -> lockSubscription
                        else -> ""
                    }
                    append(stringResource(R.string.focus_lock_locked, lockSource))
                }
                if (state.interceptConfig?.enabled == true) {
                    append(fullscreenIntercept)
                }
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier.size(36.dp),
            ) {
                PerfIcon(
                    imageVector = PerfIcon.Mindful,
                    tint = if (state.interceptConfig?.enabled == true) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = onLockClick,
                modifier = Modifier.size(36.dp),
            ) {
                PerfIcon(
                    imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History,
                    tint = if (state.isLocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
