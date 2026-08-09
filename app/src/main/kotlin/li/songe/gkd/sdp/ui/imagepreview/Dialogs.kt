@file:JvmName("ImagePreviewDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.Image

internal sealed interface ImagePreviewDialogAction { data object Dismiss : ImagePreviewDialogAction }
