@file:JvmName("AppBlockerDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.BlockTimeRule
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.ui.component.formatDurationLocalized
import li.songe.gkd.sdp.R

@Composable
internal fun AppBlockerTemplatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (BlockTimeRule.Companion.TimeTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_be8a21a3ed)) },
        text = {
            LazyColumn {
                items(BlockTimeRule.Companion.TEMPLATES) { template ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(template) }
                            .padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(template.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(template.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockSheet(
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    title: String,
    description: String,
    currentLockEndTime: Long?,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    val durationOptions = listOf(
        480 to stringResource(R.string.duration_option_8h),
        1440 to stringResource(R.string.duration_option_1d),
        4320 to stringResource(R.string.duration_option_3d),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentLockEndTime != null && currentLockEndTime > System.currentTimeMillis()) {
                val remaining = currentLockEndTime - System.currentTimeMillis()
                val remainingMinutes = (remaining / 60000).coerceAtLeast(0)
                val remainingText = formatDurationLocalized(remainingMinutes * 60_000L)
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_1090ec0cd1, remainingText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_80e9287545),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durationOptions.forEach { (minutes, label) ->
                    val isSelected = !state.isCustomLockDuration && state.selectedLockDuration == minutes
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable {
                                callbacks.onCustomLockDurationChange(false)
                                callbacks.onSelectedLockDurationChange(minutes)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    callbacks.onCustomLockDurationChange(!state.isCustomLockDuration)
                }
            ) {
                Switch(
                    checked = state.isCustomLockDuration,
                    onCheckedChange = callbacks.onCustomLockDurationChange,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(li.songe.gkd.sdp.app.getString(R.string.s_ea6dccc0a6), style = MaterialTheme.typography.bodyMedium)
            }

            if (state.isCustomLockDuration) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.customLockDaysText,
                        onValueChange = callbacks.onCustomLockDaysTextChange,
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_c3304d1e49)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.customLockHoursText,
                        onValueChange = callbacks.onCustomLockHoursTextChange,
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_99f6904ff3)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLock,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(li.songe.gkd.sdp.app.getString(R.string.s_648f1e98b5))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
