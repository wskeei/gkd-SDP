@file:JvmName("AppBlockerScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable

/** Small host; rendering sections stay isolated from navigation and service callers. */
@Composable
fun AppBlockerPage() = AppBlockerPageSections()
