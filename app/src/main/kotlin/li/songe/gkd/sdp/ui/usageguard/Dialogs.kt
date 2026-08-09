@file:JvmName("UsageGuardDialogs")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.util.UsageGuardPolicy

@Composable
internal fun UsageGuardDialogs(
    state: UsageGuardSettingsRenderState,
) {
    val context = state.context
    val vm = state.vm
    val settings = state.settings
    val appInfoMap = state.appInfoMap
    val selectedTargetApps = state.selectedTargetApps
    val whitelistApps = state.whitelistApps
    val globalOverrideApps = state.globalOverrideApps
    val profileMap = state.profileMap
    val showSelectedPicker = state.showSelectedPicker
    val showWhitelistPicker = state.showWhitelistPicker
    val showOverridePicker = state.showOverridePicker
    val appAction = state.appAction
    if (showSelectedPicker.value) {
        AppPickerDialog(
            currentApps = selectedTargetApps,
            onDismiss = { showSelectedPicker.value = false },
            onConfirm = {
                vm.saveSelectedTargets(it)
                showSelectedPicker.value = false
            },
        )
    }
    if (showWhitelistPicker.value) {
        AppPickerDialog(
            currentApps = whitelistApps,
            onDismiss = { showWhitelistPicker.value = false },
            onConfirm = {
                vm.saveWhitelist(it)
                showWhitelistPicker.value = false
            },
        )
    }
    if (showOverridePicker.value) {
        AppPickerDialog(
            currentApps = globalOverrideApps,
            onDismiss = { showOverridePicker.value = false },
            onConfirm = {
                vm.saveGrantModeOverrideApps(it)
                showOverridePicker.value = false
            },
        )
    }
    appAction.value?.let { target ->
        val currentGrantMode = profileMap[target.appId]?.grantMode ?: settings.usageGuardDefaultGrantMode
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { appAction.value = null }, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(appInfoMap[target.appId]?.name ?: target.appId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (target.scope != UsageGuardActionScope.Whitelist) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                            onClick = {
                                vm.moveSelectedAppToGrantMode(target.appId, UsageGuardPolicy.GRANT_MODE_STRICT)
                                appAction.value = null
                            },
                            label = { Text("严格模式") },
                        )
                        FilterChip(
                            selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                            onClick = {
                                vm.moveSelectedAppToGrantMode(target.appId, UsageGuardPolicy.GRANT_MODE_RESUMABLE)
                                appAction.value = null
                            },
                            label = { Text("普通模式") },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        when (target.scope) {
                            UsageGuardActionScope.Selected -> vm.saveSelectedTargets(selectedTargetApps - target.appId)
                            UsageGuardActionScope.Whitelist -> vm.saveWhitelist(whitelistApps - target.appId)
                            UsageGuardActionScope.Override -> vm.clearAppGrantModeOverride(target.appId)
                        }
                        appAction.value = null
                    },
                ) {
                    Text(
                        when (target.scope) {
                            UsageGuardActionScope.Selected -> "移出受控应用"
                            UsageGuardActionScope.Whitelist -> "移出白名单"
                            UsageGuardActionScope.Override -> "移除模式覆盖"
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

}
