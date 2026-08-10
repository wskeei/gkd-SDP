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
import li.songe.gkd.sdp.data.FocusRule
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockRuleSheet(
    vm: FocusModeVm,
    rule: FocusRule,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (rule.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_2201b864c9),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.s_b292eb1d6a),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (rule.isCurrentlyLocked) {
                val remainingMinutes = ((rule.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text(
                    text = stringResource(R.string.s_a6179baa57, )${remainingMinutes / 60}小时${remainingMinutes % 60}分钟stringResource(R.string.s_97dccf7b6e)${remainingMinutes}分钟stringResource(R.string.s_c2b7df6201),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 预设时长
            val presets = listOf(
                480 to "8小时",
                1440 to "1天",
                4320 to "3天"
            )
            presets.forEach { (minutes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Checkbox(
                        checked = !vm.isCustomLockDuration && vm.selectedLockDuration == minutes,
                        onCheckedChange = {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
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
                    .clickable { vm.isCustomLockDuration = true }
                    .padding(vertical = 12.dp)
            ) {
                Checkbox(
                    checked = vm.isCustomLockDuration,
                    onCheckedChange = { vm.isCustomLockDuration = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.s_c493338e8c))
            }

            if (vm.isCustomLockDuration) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.customLockDaysText,
                        onValueChange = { vm.customLockDaysText = it },
                        label = { Text(stringResource(R.string.s_c3304d1e49)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.customLockHoursText,
                        onValueChange = { vm.customLockHoursText = it },
                        label = { Text(stringResource(R.string.s_99f6904ff3)) },
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
                Text(if (rule.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_648f1e98b5))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
