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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

data class LockDurationRequest(
    val durationMinutes: Int,
    val isCustom: Boolean,
    val daysText: String,
    val hoursText: String,
)

@Composable
fun MindfulPauseSheet(
    target: PauseTarget,
    onConfirm: (Boolean, Int, String) -> Unit
) {
    var enabled by remember { mutableStateOf(target.config?.enabled ?: target.initialEnabled) }
    // Cooldown is hardcoded to 10s by request
    val cooldown = 10
    var message by remember {
        mutableStateOf(
            target.config?.message ?: li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message),
        )
    }

    val isBatch = target.groupKey == null

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = if (isBatch) stringResource(R.string.s_de4579c740) else stringResource(R.string.s_9118783aea),
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
            Text(stringResource(R.string.s_25d9aca60f), style = MaterialTheme.typography.titleMedium)

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
            label = { Text(stringResource(R.string.s_d28f049f02)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.s_3e42de41c7),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onConfirm(enabled, cooldown, message) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.s_817af1870c))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


@Composable
@android.annotation.SuppressLint("NonObservableLocale")
fun LockDurationSheet(
    targetName: String,
    currentEndTime: Long,
    onConfirm: (LockDurationRequest) -> Unit
) {
    val isLocked = currentEndTime > System.currentTimeMillis()
    var selectedDuration by remember { mutableStateOf(480) }
    var isCustomDuration by remember { mutableStateOf(false) }
    var customDaysText by remember { mutableStateOf("") }
    var customHoursText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isLocked) stringResource(R.string.s_8a8478347d, (targetName).toString()) else stringResource(R.string.s_9cb5b4660f, (targetName).toString()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLocked) {
            val date = java.util.Date(currentEndTime)
            val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            Text(
                text = stringResource(R.string.s_4bce9796c3, (formatter.format(date)).toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = if (isLocked) stringResource(R.string.s_dd8a138761) else stringResource(R.string.s_6806d16ee2),
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
                    480 to stringResource(R.string.duration_option_8h),
                    1440 to stringResource(R.string.duration_option_1d),
                    4320 to stringResource(R.string.duration_option_3d),
                )
                options.forEach { (duration, label) ->
                    TextButton(
                        onClick = {
                            selectedDuration = duration
                            isCustomDuration = false
                        },
                        modifier = Modifier.weight(1f),
                        border = if (!isCustomDuration && selectedDuration == duration)
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                    ) {
                        Text(
                            text = label,
                            color = if (!isCustomDuration && selectedDuration == duration)
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
                    onClick = { isCustomDuration = true },
                    modifier = Modifier.width(100.dp),
                     border = if (isCustomDuration)
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                ) {
                    Text(
                        text = stringResource(R.string.s_c493338e8c),
                        color = if (isCustomDuration)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (isCustomDuration) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customDaysText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    customDaysText = newValue
                                }
                            },
                            label = { Text(stringResource(R.string.s_c3304d1e49)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHoursText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    customHoursText = newValue
                                }
                            },
                            label = { Text(stringResource(R.string.s_99f6904ff3)) },
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
            onClick = {
                onConfirm(
                    LockDurationRequest(
                        durationMinutes = selectedDuration,
                        isCustom = isCustomDuration,
                        daysText = customDaysText,
                        hoursText = customHoursText,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLocked) stringResource(R.string.s_89120c94be) else stringResource(R.string.s_3718c68dfd))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
