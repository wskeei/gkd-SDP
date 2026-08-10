@file:JvmName("ImagePreviewRouteContract")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.Image
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ImagePreviewItem(
    val uri: String,
    val title: String? = null,
    val titles: List<String> = emptyList(),
)


@Serializable
data class ImagePreviewRoute(
    val title: String? = null,
    val uri: String? = null,
    val uris: List<String> = emptyList(),
    val items: List<ImagePreviewItem> = emptyList(),
) : NavKey
