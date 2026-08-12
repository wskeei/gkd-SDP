@file:JvmName("AdvancedSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.component.CustomOutlinedTextField
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.selector.Selector
import androidx.compose.ui.res.stringResource

@Composable
internal fun AdvancedPageSections(
    uiState: AdvancedUiState,
    callbacks: AdvancedPageCallbacks,
) {
    AdvancedPortDialog(
        show = uiState.showEditPortDlg,
        store = uiState.store,
        onDismiss = callbacks.onDismissEditPortDialog,
        onConfirm = callbacks.onApplyPort,
    )
    AdvancedShizukuDialog(
        show = uiState.showShizukuState,
        onDismiss = callbacks.onDismissShizukuDialog,
    )
    AdvancedCaptureScreenshotDialog(
        show = uiState.showCaptureScreenshotDlg,
        store = uiState.store,
        onDismiss = callbacks.onDismissCaptureScreenshotDialog,
        onHelp = callbacks.onOpenCaptureHelp,
        onConfirm = callbacks.onApplyCaptureScreenshot,
    )
    AdvancedPageContent(
        uiState = uiState,
        callbacks = callbacks,
    )
}

@Composable
private fun AdvancedPortDialog(
    show: Boolean,
    store: SettingsStore,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    if (!show) return
    val portRange = remember { 1000 to 65535 }
    val placeholderText = li.songe.gkd.sdp.app.getString(
        R.string.advanced_port_placeholder,
        portRange.first.toString(),
        portRange.second.toString(),
    )
    var value by remember { mutableStateOf(store.httpServerPort.toString()) }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = stringResource(R.string.s_6f77ee7c5c)) },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = placeholderText) },
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().autoFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        li.songe.gkd.sdp.app.getString(R.string.s_201d216690, value.length.toString()),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || newPort !in portRange.first..portRange.second) {
                        toast(placeholderText)
                        return@TextButton
                    }
                    onConfirm(newPort)
                },
            ) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_4d0b4688c7)) }
        },
    )
}

@Composable
private fun AdvancedShizukuDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        title = { Text(text = stringResource(R.string.s_ac3cc79f91)) },
        text = {
            val states = shizukuContextFlow.collectAsStateWithLifecycle().value.states
            Column {
                states.forEach { (name, value) ->
                    Text(
                        text = name,
                        textDecoration = if (value != null) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.s_dd3760c80a)) }
        },
    )
}

@Composable
private fun AdvancedCaptureScreenshotDialog(
    show: Boolean,
    store: SettingsStore,
    onDismiss: () -> Unit,
    onHelp: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    if (!show) return
    var appIdValue by remember { mutableStateOf(store.screenshotTargetAppId) }
    var eventSelectorValue by remember { mutableStateOf(store.screenshotEventSelector) }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.s_ee5db675e1))
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    onClick = throttle {
                        onDismiss()
                        onHelp()
                    },
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CustomOutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_be8af550f3)) },
                    value = appIdValue,
                    placeholder = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_9fccef0027)) },
                    onValueChange = { appIdValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                CustomOutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a08049752b)) },
                    value = eventSelectorValue,
                    placeholder = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_ea72227d80)) },
                    onValueChange = { eventSelectorValue = it },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = throttle {
                if (appIdValue.isNotEmpty() && !appInfoMapFlow.value.contains(appIdValue)) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_34e21ea99c))
                    return@throttle
                }
                if (eventSelectorValue.isNotEmpty() && Selector.parseOrNull(eventSelectorValue) == null) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_8c9fbc6ef9))
                    return@throttle
                }
                onConfirm(appIdValue, eventSelectorValue)
            }) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_4d0b4688c7)) }
        },
    )
}
