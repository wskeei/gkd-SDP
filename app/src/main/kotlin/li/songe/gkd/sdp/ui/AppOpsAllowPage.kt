package li.songe.gkd.sdp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.permission.PermissionState
import li.songe.gkd.sdp.permission.appOpsRestrictStateList
import li.songe.gkd.sdp.permission.appOpsRestrictedFlow
import li.songe.gkd.sdp.ui.component.AuthButtonGroup
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.ManualAuthDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.util.getShareApkFile
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.saveFileToDownloads
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Serializable
data object AppOpsAllowRoute : NavKey

@Composable
fun AppOpsAllowPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<AppOpsAllowVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val appOpsRestricted by appOpsRestrictedFlow.collectAsStateWithLifecycle()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                mainVm.popPage()
            })
        }, title = {
            Text(text = app.getString(R.string.s_ffa17c3049))
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            if (appOpsRestricted) {
                Column(
                    modifier = Modifier
                        .padding(itemHorizontalPadding, 0.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.s_0fd5f2bdd6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        appOpsRestrictStateList.forEach { RestrictItem(it) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AuthButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        buttons = listOf(
                            "Shizuku 授权" to vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                mainVm.guardShizukuContext()
                                toast(app.getString(R.string.s_027b905228))
                            },
                            "命令授权" to {
                                vm.showCopyDlgFlow.value = true
                            },
                            "卸载重装" to {
                                mainVm.dialogFlow.updateDialogOptions(
                                    title = app.getString(R.string.s_a50703b6a2),
                                    text = app.getString(R.string.s_150e6661aa),
                                    dismissText = "导出应用",
                                    dismissAction = {
                                        mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                            context.saveFileToDownloads(getShareApkFile())
                                        }
                                    },
                                    confirmText = "关闭",
                                )
                            }
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
            if (!appOpsRestricted) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                EmptyText(text = stringResource(R.string.s_26bb571105))
            }
        }
    }

    val showCopyDlg by vm.showCopyDlgFlow.collectAsStateWithLifecycle()
    val commandText by gkdStartCommandTextFlow.collectAsStateWithLifecycle()
    LaunchedEffect(showCopyDlg) {
        if (showCopyDlg) refreshGkdStartCommandText()
    }
    ManualAuthDialog(
        commandText = commandText,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        }
    )
}

@Composable
private fun RestrictItem(state: PermissionState) {
    if (!state.stateFlow.collectAsStateWithLifecycle().value) {
        Row {
            val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
            val size = 5.dp
            Spacer(
                modifier = Modifier
                    .padding(vertical = (lineHeightDp - size) / 2)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .size(size)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                style = MaterialTheme.typography.titleMedium,
                text = state.name,
            )
        }
    }
}
