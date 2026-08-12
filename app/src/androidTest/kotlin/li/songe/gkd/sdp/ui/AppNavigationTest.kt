package li.songe.gkd.sdp.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.SdpUiTestHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun topLevelDestinationsAreClickable() {
        composeRule.onNodeWithTag("nav_overview").assertExists()
        composeRule.onNodeWithTag("nav_self_control", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("nav_rules", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_page_root", useUnmergedTree = true).assertExists()
    }
}
