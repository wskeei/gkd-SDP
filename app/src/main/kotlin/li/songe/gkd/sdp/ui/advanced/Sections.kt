@file:JvmName("AdvancedSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.remote.CleartextOriginAuthorizations
import li.songe.gkd.sdp.remote.RemoteListenMode
import li.songe.gkd.sdp.remote.RemoteScope
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.ButtonService
import li.songe.gkd.sdp.service.EventService
import li.songe.gkd.sdp.service.HttpService
import li.songe.gkd.sdp.service.ScreenshotService
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.shizuku.updateBinderMutex
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.AuthCard
import li.songe.gkd.sdp.ui.component.CustomOutlinedTextField
import li.songe.gkd.sdp.ui.component.PerfCustomIconButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SettingItem
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.selector.Selector
import androidx.compose.ui.res.stringResource

@Composable
fun AdvancedPageSections() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AdvancedVm>()
    val store by storeFlow.collectAsStateWithLifecycle()

    val showEditPortDlg = vm.showEditPortDlgFlow.asMutableState()
    val showShizukuState = vm.showShizukuStateFlow.asMutableState()
    val showCaptureScreenshotDlg = vm.showCaptureScreenshotDlgFlow.asMutableState()
    AdvancedPortDialog(showEditPortDlg = showEditPortDlg, store = store)
    AdvancedShizukuDialog(showShizukuState = showShizukuState)
    AdvancedCaptureScreenshotDialog(
        showDialog = showCaptureScreenshotDlg,
        mainVm = mainVm,
        store = store,
    )
    val showHttpSettingDlg = rememberSaveable { mutableStateOf(false) }

    AdvancedPageContent(
        context = context,
        mainVm = mainVm,
        vm = vm,
        store = store,
        showEditPortDlg = showEditPortDlg,
        showShizukuState = showShizukuState,
        showCaptureScreenshotDlg = showCaptureScreenshotDlg,
        showHttpSettingDlg = showHttpSettingDlg,
    )
}

@Composable
private fun AdvancedPortDialog(
    showEditPortDlg: androidx.compose.runtime.MutableState<Boolean>,
    store: li.songe.gkd.sdp.store.SettingsStore,
) {
    if (!showEditPortDlg.value) return
    val portRange = remember { 1000 to 65535 }
    val placeholderText = remember { "请输入 ${portRange.first}-${portRange.second} 的整数" }
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
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_201d216690, value.length), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                },
            )
        },
        onDismissRequest = { showEditPortDlg.value = false },
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || newPort !in portRange.first..portRange.second) {
                        toast(placeholderText)
                        return@TextButton
                    }
                    showEditPortDlg.value = false
                    if (newPort != store.httpServerPort) {
                        storeFlow.value = store.copy(httpServerPort = newPort)
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                    }
                },
            ) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = { TextButton(onClick = { showEditPortDlg.value = false }) { Text(stringResource(R.string.s_4d0b4688c7)) } },
    )
}

@Composable
private fun AdvancedShizukuDialog(
    showShizukuState: androidx.compose.runtime.MutableState<Boolean>,
) {
    if (!showShizukuState.value) return
    val onDismissRequest = { showShizukuState.value = false }
    AlertDialog(
        title = { Text(text = stringResource(R.string.s_ac3cc79f91)) },
        text = {
            val states = shizukuContextFlow.collectAsStateWithLifecycle().value.states
            Column {
                states.forEach { (name, value) ->
                    Text(text = name, textDecoration = if (value != null) null else TextDecoration.LineThrough)
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(text = stringResource(R.string.s_dd3760c80a)) } },
    )
}

@Composable
private fun AdvancedCaptureScreenshotDialog(
    showDialog: androidx.compose.runtime.MutableState<Boolean>,
    mainVm: MainViewModel,
    store: li.songe.gkd.sdp.store.SettingsStore,
) {
    if (!showDialog.value) return
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
                        showDialog.value = false
                        mainVm.navigateWebPage(ShortUrlSet.URL15)
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
        onDismissRequest = { showDialog.value = false },
        confirmButton = {
            TextButton(onClick = throttle {
                if (appIdValue == store.screenshotTargetAppId && eventSelectorValue == store.screenshotEventSelector) {
                    showDialog.value = false
                    return@throttle
                }
                if (appIdValue.isNotEmpty() && !appInfoMapFlow.value.contains(appIdValue)) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_34e21ea99c))
                    return@throttle
                }
                if (eventSelectorValue.isNotEmpty() && Selector.parseOrNull(eventSelectorValue) == null) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_8c9fbc6ef9))
                    return@throttle
                }
                storeFlow.update {
                    it.copy(
                        screenshotTargetAppId = appIdValue,
                        screenshotEventSelector = eventSelectorValue,
                    )
                }
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                showDialog.value = false
            }) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = { TextButton(onClick = { showDialog.value = false }) { Text(stringResource(R.string.s_4d0b4688c7)) } },
    )
}
