@file:JvmName("SettingsRoute")

package li.songe.gkd.sdp.ui.home

import androidx.compose.runtime.Composable

/**
 * Route-level entry for the settings tab.  The tab is represented by
 * [li.songe.gkd.sdp.ui.home.HomeRoute]; this wrapper keeps navigation from
 * reaching into the section implementation directly.
 */
@Composable
fun useSettingsRoute(): ScaffoldExt = useSettingsPageSections()
