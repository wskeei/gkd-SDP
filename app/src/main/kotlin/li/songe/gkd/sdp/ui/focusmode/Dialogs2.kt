@file:JvmName("FocusModeDialogs21")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.ui.component.formatDurationLocalized
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockRuleSheet(
    state: FocusModeUiState,
    callbacks: FocusModeCallbacks,
    rule: FocusRule,
) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismissRuleLock,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (rule.isCurrentlyLocked) li.songe.gkd.sdp.app.getString(R.string.s_eae5fd957e) else li.songe.gkd.sdp.app.getString(R.string.s_2201b864c9),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_b292eb1d6a),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (rule.isCurrentlyLocked) {
                val remainingMinutes = ((rule.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text(
                    text = li.songe.gkd.sdp.app.getString(
                        R.string.s_1090ec0cd1,
                        formatDurationLocalized(remainingMinutes * 60_000L),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 预设时长
            val presets = listOf(
                480 to stringResource(R.string.duration_option_8h),
                1440 to stringResource(R.string.duration_option_1d),
                4320 to stringResource(R.string.duration_option_3d),
            )
            presets.forEach { (minutes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            callbacks.onSelectedLockDurationChange(minutes)
                            callbacks.onCustomLockDurationChange(false)
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Checkbox(
                        checked = !state.isCustomLockDuration && state.selectedLockDuration == minutes,
                        onCheckedChange = {
                            callbacks.onSelectedLockDurationChange(minutes)
                            callbacks.onCustomLockDurationChange(false)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }

            // 自定义时长
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { callbacks.onCustomLockDurationChange(true) }
                    .padding(vertical = 12.dp)
            ) {
                Checkbox(
                    checked = state.isCustomLockDuration,
                    onCheckedChange = callbacks.onCustomLockDurationChange,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(li.songe.gkd.sdp.app.getString(R.string.s_c493338e8c))
            }

            if (state.isCustomLockDuration) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
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
                onClick = callbacks.onLockRuleTarget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (rule.isCurrentlyLocked) li.songe.gkd.sdp.app.getString(R.string.s_eae5fd957e) else li.songe.gkd.sdp.app.getString(R.string.s_648f1e98b5))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
