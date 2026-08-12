@file:JvmName("ImagePreviewPresenter0")

package li.songe.gkd.sdp.ui

fun ImagePreviewUiState.reduce(action: ImagePreviewAction): ImagePreviewUiState = when (action) {
    ImagePreviewAction.ToggleBars -> copy(showBars = !showBars)
    is ImagePreviewAction.ShowPage -> copy(
        currentPage = action.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
    )
}

fun imagePreviewUiState(route: ImagePreviewRoute): ImagePreviewUiState = ImagePreviewUiState(
    items = when {
        route.items.isNotEmpty() -> route.items
        route.uris.isNotEmpty() -> route.uris.map { ImagePreviewItem(it) }
        route.uri != null -> listOf(ImagePreviewItem(uri = route.uri))
        else -> emptyList()
    },
)
