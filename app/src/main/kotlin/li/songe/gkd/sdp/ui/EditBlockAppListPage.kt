package li.songe.gkd.sdp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.store.blockMatchAppListFlow
import li.songe.gkd.sdp.ui.component.MultiTextField
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R

@Serializable
data object EditBlockAppListRoute : NavKey

@Composable
fun EditBlockAppListPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<EditBlockAppListVm>()
    val onBack = throttle(vm.viewModelScope.launchAsFn {
        if (vm.getChangedSet() != null) {
            context.justHideSoftInput()
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_ab3656a956),
                text = li.songe.gkd.sdp.app.getString(R.string.s_aebc195621),
            )
        } else {
            context.hideSoftInput()
        }
        mainVm.popPage()
    })
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier, topBar = {
        PerfTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = onBack,
                )
            },
            title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_7395ba05d0)) },
            actions = {
                PerfIconButton(
                    imageVector = PerfIcon.Save,
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        val newSet = vm.getChangedSet()
                        if (newSet != null) {
                            blockMatchAppListFlow.value = newSet
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                        } else {
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_fff8cc4d94))
                        }
                        context.hideSoftInput()
                        mainVm.popPage()
                    })
                )
            }
        )
    }) { contentPadding ->
        MultiTextField(
            modifier = Modifier.scaffoldPadding(contentPadding),
            textFlow = vm.textFlow,
            indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value
        )
    }
}