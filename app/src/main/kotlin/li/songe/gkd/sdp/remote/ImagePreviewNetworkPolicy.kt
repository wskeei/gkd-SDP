package li.songe.gkd.sdp.remote

import java.net.URI

sealed interface ImagePreviewDecision {
    val isAllowed: Boolean
    val errorCode: String?

    data object Allowed : ImagePreviewDecision {
        override val isAllowed = true
        override val errorCode: String? = null
    }

    data object HttpBlocked : ImagePreviewDecision {
        override val isAllowed = false
        override val errorCode: String = ImagePreviewNetworkPolicy.ERROR_HTTP_BLOCKED
    }

    data object InvalidNetworkUri : ImagePreviewDecision {
        override val isAllowed = false
        override val errorCode: String = ImagePreviewNetworkPolicy.ERROR_INVALID_NETWORK_URI
    }
}

object ImagePreviewNetworkPolicy {
    const val ERROR_HTTP_BLOCKED = "IMAGE_HTTP_BLOCKED"
    const val ERROR_INVALID_NETWORK_URI = "IMAGE_INVALID_NETWORK_URI"

    private val networkSchemes = setOf(
        "http",
        "https",
        "ftp",
        "ws",
        "wss",
        "rtsp",
        "javascript",
        "data",
    )

    fun isNetworkUri(uri: String): Boolean {
        val scheme = runCatching { URI(uri).scheme?.lowercase() }.getOrNull()
        return scheme in networkSchemes
    }

    fun isLocalUri(uri: String): Boolean = !isNetworkUri(uri)

    fun isDisplayAllowed(uri: String): Boolean =
        isLocalUri(uri) || decideNetwork(uri).isAllowed

    fun decideNetwork(uri: String): ImagePreviewDecision {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return ImagePreviewDecision.InvalidNetworkUri
        val scheme = parsed.scheme?.lowercase()
        return when (scheme) {
            "https" -> {
                val hostValid = !parsed.host.isNullOrBlank() &&
                    parsed.rawUserInfo == null &&
                    parsed.port in -1..65535
                if (hostValid) ImagePreviewDecision.Allowed else ImagePreviewDecision.InvalidNetworkUri
            }
            "http" -> ImagePreviewDecision.HttpBlocked
            in networkSchemes -> ImagePreviewDecision.InvalidNetworkUri
            null -> ImagePreviewDecision.InvalidNetworkUri
            else -> ImagePreviewDecision.InvalidNetworkUri
        }
    }
}
