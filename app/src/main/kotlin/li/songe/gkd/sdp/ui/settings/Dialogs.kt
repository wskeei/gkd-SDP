@file:JvmName("SettingsDialogs")

package li.songe.gkd.sdp.ui.home

import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.backup.BackupCatalog
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.backup.BackupResult
import li.songe.gkd.sdp.backup.PreparedBackupImport
import li.songe.gkd.sdp.backup.BackupSourceFormat
import li.songe.gkd.sdp.settings.SettingsFormPolicy
import li.songe.gkd.sdp.ui.AboutRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.BlockA11yAppListRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.component.CustomOutlinedTextField
import li.songe.gkd.sdp.ui.component.FullscreenDialog
import li.songe.gkd.sdp.ui.component.PerfCustomIconButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SettingItem
import li.songe.gkd.sdp.ui.component.TextListDialog
import li.songe.gkd.sdp.ui.component.TextMenu
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.component.useScrollBehaviorState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.store.SettingsStore
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.ui.style.DimensionTokens

@Composable
internal fun SettingsTextDialogs(
    context: MainActivity,
    store: SettingsStore,
    showToastInputDlg: Boolean,
    showNotifTextInputDlg: Boolean,
    onDismissToastInput: () -> Unit,
    onConfirmToastInput: (String) -> Unit,
    onShowToastHelp: () -> Unit,
    onDismissNotifInput: () -> Unit,
    onConfirmNotifInput: (String, String) -> Unit,
    onShowNotifHelp: () -> Unit,
) {
    SettingsToastDialog(
        store = store,
        showToastInputDlg = showToastInputDlg,
        onDismissToastInput = onDismissToastInput,
        onConfirmToastInput = onConfirmToastInput,
        onShowToastHelp = onShowToastHelp,
    )
    SettingsNotificationDialog(
        context = context,
        store = store,
        showNotifTextInputDlg = showNotifTextInputDlg,
        onDismissNotifInput = onDismissNotifInput,
        onConfirmNotifInput = onConfirmNotifInput,
        onShowNotifHelp = onShowNotifHelp,
    )
}

@Composable
private fun SettingsToastDialog(
    store: SettingsStore,
    showToastInputDlg: Boolean,
    onDismissToastInput: () -> Unit,
    onConfirmToastInput: (String) -> Unit,
    onShowToastHelp: () -> Unit,
) {
    if (!showToastInputDlg) return
    var value by remember { mutableStateOf(store.actionToast) }
    val maxCharLen = 64
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.s_5bf7ff408f))
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = stringResource(R.string.settings_copy_rules),
                    onClickLabel = stringResource(R.string.settings_open_copy_rules),
                    onClick = throttle(onShowToastHelp),
                )
            }
        },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_29207cc695)) },
                onValueChange = { value = it.take(maxCharLen) },
                supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_61485b8822, (value.length).toString(), (maxCharLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                modifier = Modifier.fillMaxWidth().autoFocus(),
            )
        },
        onDismissRequest = onDismissToastInput,
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = { onConfirmToastInput(value) },
            ) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissToastInput) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        },
    )
}

@Composable
private fun SettingsNotificationDialog(
    context: MainActivity,
    store: SettingsStore,
    showNotifTextInputDlg: Boolean,
    onDismissNotifInput: () -> Unit,
    onConfirmNotifInput: (String, String) -> Unit,
    onShowNotifHelp: () -> Unit,
) {
    if (!showNotifTextInputDlg) return
    var titleValue by remember { mutableStateOf(store.customNotifTitle) }
    var textValue by remember {
        mutableStateOf(
            store.customNotifText.ifBlank {
                context.getString(R.string.notif_custom_text_default)
            },
        )
    }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.s_ce7c7d71a7))
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = stringResource(R.string.settings_copy_rules),
                    onClickLabel = stringResource(R.string.settings_open_copy_rules),
                    onClick = throttle(onShowNotifHelp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val titleMaxLen = 32
                val textMaxLen = 64
                CustomOutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e6dc2df4a4)) },
                    value = titleValue,
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_d8eb8652a1)) },
                    onValueChange = { titleValue = it.take(titleMaxLen).filter { c -> c !in "\n\r" } },
                    supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_2990881615, (titleValue.length).toString(), (titleMaxLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_8344831e7c)) },
                    value = textValue,
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_d8eb8652a1)) },
                    onValueChange = { textValue = it.take(textMaxLen) },
                    supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_98362e17dd, (textValue.length).toString(), (textMaxLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                    contentPadding = PaddingValues(12.dp),
                )
            }
        },
        onDismissRequest = onDismissNotifInput,
        confirmButton = {
            TextButton(onClick = {
                context.justHideSoftInput()
                onConfirmNotifInput(titleValue, textValue)
            }) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissNotifInput) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        },
    )
}

@Composable
internal fun BlockA11yDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    shizukuOk: Boolean,
    statusRunning: Boolean,
    ignoreBatteryOptimizations: Boolean,
    onGuardShizuku: () -> Unit,
    onRequestStatusService: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onSwitchRecentApps: () -> Unit,
) = FullscreenDialog(onDismissRequest) {
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        onClickLabel = stringResource(R.string.dialog_close),
                        onClick = onDismissRequest,
                    )
                },
                title = {
                    Text(text = li.songe.gkd.sdp.app.getString(R.string.s_86613e925d))
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = shizukuOk && statusRunning && ignoreBatteryOptimizations,
                    onClick = onConfirm,
                ) {
                    Text(text = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5))
                }
                Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = DimensionTokens.SpacingBase)
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_be7bf1f6b3))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_59e2c8e61d), style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(text = stringResource(R.string.block_a11y_warning_touch))
                    RequiredTextItem(text = stringResource(R.string.block_a11y_warning_other_a11y))
                    RequiredTextItem(text = stringResource(R.string.block_a11y_warning_background))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_b412fa069d), style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(
                        text = stringResource(R.string.block_a11y_require_shizuku),
                        enabled = !shizukuOk,
                        imageVector = if (shizukuOk) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = onGuardShizuku,
                    )
                    RequiredTextItem(
                        text = stringResource(R.string.block_a11y_require_notification),
                        enabled = !statusRunning,
                        imageVector = if (statusRunning) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = onRequestStatusService,
                    )
                    RequiredTextItem(
                        text = stringResource(R.string.block_a11y_require_battery),
                        enabled = !ignoreBatteryOptimizations,
                        imageVector = if (ignoreBatteryOptimizations) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClickLabel = stringResource(R.string.settings_open_battery_settings),
                        onClick = onOpenBatterySettings,
                    )
                    RequiredTextItem(
                        text = stringResource(R.string.block_a11y_optional_autostart),
                        enabled = true,
                        imageVector = PerfIcon.OpenInNew,
                        onClickLabel = stringResource(R.string.settings_open_app_details),
                        onClick = onOpenAppDetails,
                    )
                    RequiredTextItem(
                        text = stringResource(R.string.block_a11y_optional_recent_lock),
                        enabled = true,
                        imageVector = PerfIcon.OpenInNew,
                        onClickLabel = stringResource(R.string.settings_open_app_details),
                        onClick = onSwitchRecentApps,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_d0cd80bc26))
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun RequiredTextItem(
    text: String,
    imageVector: ImageVector? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .run {
                if (onClick != null) {
                    clickable(
                        enabled = enabled,
                        onClick = throttle(onClick),
                        onClickLabel = onClickLabel
                    )
                } else {
                    this
                }
            }
            .padding(horizontal = 4.dp),
    ) {
        val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
        Spacer(
            modifier = Modifier
                .padding(vertical = (lineHeightDp - 4.dp) / 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .size(4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
        if (imageVector != null) {
            PerfIcon(
                imageVector = imageVector,
                modifier = Modifier.iconTextSize(),
            )
        }
    }

}
