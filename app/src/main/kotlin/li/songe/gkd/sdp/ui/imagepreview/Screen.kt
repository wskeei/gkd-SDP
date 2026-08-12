@file:JvmName("ImagePreviewScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import li.songe.gkd.sdp.ui.share.LocalMainViewModel

@Composable
fun ImagePreviewPage(route: ImagePreviewRoute) {
    val mainVm = LocalMainViewModel.current
    val initialState = remember(route) { imagePreviewUiState(route) }
    var uiState by remember(route) { mutableStateOf(initialState) }
    val pagerState = rememberPagerState(pageCount = { initialState.items.size.coerceAtLeast(1) })

    ImagePreviewPageSections(
        route = route,
        uiState = uiState,
        pagerState = pagerState,
        onBack = mainVm::popPage,
        onOpenUrl = mainVm::openUrl,
        onToggleBars = {
            uiState = uiState.reduce(ImagePreviewAction.ToggleBars)
        },
    )
}
