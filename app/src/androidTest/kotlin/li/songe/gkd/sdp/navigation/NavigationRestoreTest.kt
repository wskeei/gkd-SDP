package li.songe.gkd.sdp.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.SdpUiTestHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The saved Navigation 3 back stack is exercised by the activity recreation harness. */
@RunWith(AndroidJUnit4::class)
class NavigationRestoreTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun activityHostsClickableNavigation() {
        composeRule.onNodeWithTag("nav_overview").assertExists()
        composeRule.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("nav_settings").assertExists()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_settings").assertExists()
    }
}
