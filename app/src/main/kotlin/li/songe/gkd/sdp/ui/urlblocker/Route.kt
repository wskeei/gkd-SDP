@file:JvmName("UrlBlockerRoute")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Serializable
data object UrlBlockRoute : NavKey

/** Route boundary for the URL blocker destination. */
@Composable
internal fun UrlBlockerRoute() {
    UrlBlockerScreen()
}
