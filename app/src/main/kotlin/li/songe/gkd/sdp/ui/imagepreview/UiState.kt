@file:JvmName("ImagePreviewUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

@Immutable
data class ImagePreviewUiState(
    val items: List<ImagePreviewItem> = emptyList(),
    val currentPage: Int = 0,
    val showBars: Boolean = true,
)

sealed interface ImagePreviewAction {
    data object ToggleBars : ImagePreviewAction
    data class ShowPage(val index: Int) : ImagePreviewAction
}
