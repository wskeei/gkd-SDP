package li.songe.gkd.sdp.ui

import android.Manifest
import android.app.AppOpsManagerHidden
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.permission.Manifest_permission_GET_APP_OPS_STATS
import li.songe.gkd.sdp.permission.writeSecureSettingsState
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.ExposeService
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.shizuku.SafeAppOpsService
import li.songe.gkd.sdp.shizuku.shizukuUsedFlow
import li.songe.gkd.sdp.store.updateEnableAutomator
import li.songe.gkd.sdp.ui.component.AnimatedBooleanContent
import li.songe.gkd.sdp.ui.component.ManualAuthDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.AutomatorModeOption
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.openA11ySettings
import li.songe.gkd.sdp.util.shFolder
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.store.writeTextAtomically
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.style.DimensionTokens

@Serializable
data object AuthA11yRoute : NavKey

@Composable
fun AuthA11yPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsStateWithLifecycle()
    val commandText by gkdStartCommandTextFlow.collectAsStateWithLifecycle()
    LaunchedEffect(showCopyDlg) {
        if (showCopyDlg) refreshGkdStartCommandText()
    }
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsStateWithLifecycle()
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    val automatorMode by mainVm.automatorModeFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = {
                    mainVm.popPage()
                })
        }, title = {
            Text(text = li.songe.gkd.sdp.app.getString(R.string.s_f8b4c14ff9))
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = DimensionTokens.SpacingBase)
                    .fillMaxWidth(),
                onClick = throttle { mainVm.updateAutomatorMode(AutomatorModeOption.A11yMode) },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.A11yMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.A11yMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd)
                        .padding(start = 4.dp),
                    text = stringResource(R.string.s_5f83e7f6a1),
                    style = MaterialTheme.typography.titleSmall
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd)
                        .padding(start = 8.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "授予「无障碍权限」",
                        "无障碍关闭后需重新授权"
                    ),
                )
                AnimatedBooleanContent(
                    targetState = writeSecureSettings || a11yRunning,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = DimensionTokens.SpacingMd)
                                .padding(start = 8.dp, top = 4.dp),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_3075b35472),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DimensionTokens.SpacingMd),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = throttle { openA11ySettings() },
                            ) {
                                Text(
                                    text = li.songe.gkd.sdp.app.getString(R.string.s_34fd164246),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Text(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable(onClick = throttle {
                                        mainVm.navigateWebPage(ShortUrlSet.URL2)
                                    })
                                    .padding(horizontal = 4.dp),
                                text = li.songe.gkd.sdp.app.getString(R.string.s_2735ce6e46),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd)
                        .padding(start = 4.dp, top = 8.dp),
                    text = stringResource(R.string.s_1dd014a84e),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd)
                        .padding(start = 8.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "授予「写入安全设置权限」",
                        "应用可自行控制开关无障碍",
                    ),
                )
                AnimatedBooleanContent(
                    targetState = writeSecureSettings,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = DimensionTokens.SpacingMd)
                                .padding(start = 8.dp, top = 4.dp),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_5ae6bc88fe),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = DimensionTokens.SpacingMd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShizukuAuthButton()
                            TextButton(onClick = { vm.showCopyDlgFlow.value = true }) {
                                Text(
                                    text = li.songe.gkd.sdp.app.getString(R.string.s_92cab38651),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                )
                TextButton(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd),
                    onClick = throttle {
                        if (!writeSecureSettings) {
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_45d0618f98, (writeSecureSettingsState.name).toString()))
                        }
                        mainVm.dialogFlow.updateDialogOptions(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_ad2ea87ca3),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_cd7a98be77, (META.appName).toString())
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.s_ad2ea87ca3),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(horizontal = DimensionTokens.SpacingBase)
                    .fillMaxWidth(),
                onClick = throttle { mainVm.updateAutomatorMode(AutomatorModeOption.AutomationMode) },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.AutomationMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.AutomationMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = DimensionTokens.SpacingMd)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "自动化驱动的无障碍",
                        "不会导致界面显示异常",
                        "不会被其它应用检测为无障碍",
                        "部分应用仍需切换至无障碍模式",
                    ),
                )
                AnimatedBooleanContent(
                    targetState = shizukuUsedFlow.collectAsStateWithLifecycle().value,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = DimensionTokens.SpacingMd)
                                .padding(start = 8.dp, top = 8.dp),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_787a6e40ac),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        ShizukuAuthButton(
                            modifier = Modifier.padding(
                                start = DimensionTokens.SpacingMd
                            )
                        )
                    }
                )
                TextButton(
                    modifier = Modifier.padding(start = DimensionTokens.SpacingMd),
                    onClick = throttle {
                        mainVm.navigatePage(A11YScopeAppListRoute)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.s_3721fe11a2),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    ManualAuthDialog(
        commandText = commandText,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        },
    )
}

@Composable
private fun ShizukuAuthButton(
    modifier: Modifier = Modifier,
) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AuthA11yVm>()
    TextButton(
        modifier = modifier,
        onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            mainVm.guardShizukuContext()
            if (writeSecureSettingsState.value) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_027b905228))
                updateEnableAutomator(true)
                fixRestartAutomatorService()
            }
        })
    ) {
        Text(
            text = stringResource(R.string.s_0f0c48af67),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private val Int.appopsAllow get() = "appops set ${META.appId} ${AppOpsManagerHidden.opToName(this)} allow"
private val String.pmGrant get() = "pm grant ${META.appId} $this"

val gkdStartCommandTextFlow = MutableStateFlow("正在生成一次性授权命令…")

suspend fun refreshGkdStartCommandText() {
    gkdStartCommandTextFlow.value = "正在生成一次性授权命令…"
    gkdStartCommandTextFlow.value = runCatching {
        withContext(Dispatchers.IO) {
            val exposeFile = ExposeService.refreshExternalCommandFile()
            val commandText = listOfNotNull(
                "set -euo pipefail",
                Manifest.permission.WRITE_SECURE_SETTINGS.pmGrant,
                Manifest_permission_GET_APP_OPS_STATS.pmGrant,
                if (AndroidTarget.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS.pmGrant else null,
                AppOpsManagerHidden.OP_POST_NOTIFICATION.appopsAllow,
                AppOpsManagerHidden.OP_SYSTEM_ALERT_WINDOW.appopsAllow,
                if (AndroidTarget.Q) AppOpsManagerHidden.OP_ACCESS_ACCESSIBILITY.appopsAllow else null,
                if (AndroidTarget.TIRAMISU) AppOpsManagerHidden.OP_ACCESS_RESTRICTED_SETTINGS.appopsAllow else null,
                if (AndroidTarget.UPSIDE_DOWN_CAKE) AppOpsManagerHidden.OP_FOREGROUND_SERVICE_SPECIAL_USE.appopsAllow else null,
                if (SafeAppOpsService.supportCreateA11yOverlay) AppOpsManagerHidden.OP_CREATE_ACCESSIBILITY_OVERLAY.appopsAllow else null,
                "sh ${exposeFile.absolutePath}",
            ).joinToString("\n")
            val file = shFolder.resolve("start.sh")
            writeTextAtomically(file, commandText)
            ExposeService.restrictToOwner(file)
            "adb shell sh ${file.absolutePath}"
        }
    }.getOrElse { "授权命令生成失败，请关闭后重试" }
}

@Composable
private fun TextListItem(
    list: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val lineHeightDp = LocalDensity.current.run { style.lineHeight.toDp() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        list.forEach { text ->
            Row {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = (lineHeightDp - 4.dp) / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}
