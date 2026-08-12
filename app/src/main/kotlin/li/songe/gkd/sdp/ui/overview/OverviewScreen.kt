package li.songe.gkd.sdp.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.ui.home.useControlPage
import li.songe.gkd.sdp.ui.home.HomeVm

/**
 * Overview: service status, current mode, pending capabilities, today's
 * summary, recent triggers and quick start/stop. Rendered from the existing
 * control page content so runtime behavior stays unchanged.
 */
@Composable
fun OverviewScreen(vm: HomeVm = viewModel()) {
    val control = useControlPage(vm)
    Column(modifier = Modifier) {
        control.topBar()
        control.content(PaddingValues())
    }
}
