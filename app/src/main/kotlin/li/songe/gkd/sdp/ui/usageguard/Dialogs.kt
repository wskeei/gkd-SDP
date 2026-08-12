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
import li.songe.gkd.sdp.R

@Composable
internal fun UsageGuardDialogs(
    state: UsageGuardSettingsRenderState,
) {
    val ui = state.state
    val settings = ui.settings
    val appInfoMap = ui.appInfoMap
    val selectedTargetApps = ui.selectedTargetApps
    val whitelistApps = ui.whitelistApps
    val globalOverrideApps = ui.globalOverrideApps
    val profileMap = ui.profileMap
    if (ui.showSelectedPicker) {
        AppPickerDialog(
            currentApps = selectedTargetApps,
            onDismiss = state.onDismissSelectedPicker,
            onConfirm = {
                state.onSaveSelectedTargets(it)
                state.onDismissSelectedPicker()
            },
        )
    }
    if (ui.showWhitelistPicker) {
        AppPickerDialog(
            currentApps = whitelistApps,
            onDismiss = state.onDismissWhitelistPicker,
            onConfirm = {
                state.onSaveWhitelist(it)
                state.onDismissWhitelistPicker()
            },
        )
    }
    if (ui.showOverridePicker) {
        AppPickerDialog(
            currentApps = globalOverrideApps,
            onDismiss = state.onDismissOverridePicker,
            onConfirm = {
                state.onSaveGrantModeOverrideApps(it)
                state.onDismissOverridePicker()
            },
        )
    }
    ui.appAction?.let { target ->
        val currentGrantMode = profileMap[target.appId]?.grantMode ?: settings.usageGuardDefaultGrantMode
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = state.onCloseAppAction, sheetState = sheetState) {
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
                                state.onDispatch(
                                    UsageGuardAction.MoveSelectedAppToGrantMode(
                                        target.appId,
                                        UsageGuardPolicy.GRANT_MODE_STRICT,
                                    ),
                                )
                                state.onCloseAppAction()
                            },
                            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_cce3d12ecc)) },
                        )
                        FilterChip(
                            selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                            onClick = {
                                state.onDispatch(
                                    UsageGuardAction.MoveSelectedAppToGrantMode(
                                        target.appId,
                                        UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                                    ),
                                )
                                state.onCloseAppAction()
                            },
                            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e8a4554eb3)) },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        when (target.scope) {
                            UsageGuardActionScope.Selected ->
                                state.onSaveSelectedTargets(selectedTargetApps - target.appId)
                            UsageGuardActionScope.Whitelist ->
                                state.onSaveWhitelist(whitelistApps - target.appId)
                            UsageGuardActionScope.Override ->
                                state.onClearAppGrantModeOverride(target.appId)
                        }
                        state.onCloseAppAction()
                    },
                ) {
                    Text(
                        when (target.scope) {
                            UsageGuardActionScope.Selected -> li.songe.gkd.sdp.app.getString(R.string.s_8b3aba89d1)
                            UsageGuardActionScope.Whitelist -> li.songe.gkd.sdp.app.getString(R.string.s_817ddac3d7)
                            UsageGuardActionScope.Override -> li.songe.gkd.sdp.app.getString(R.string.s_cd732f6643)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

}
