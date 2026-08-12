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
class UsageRequestFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun selfControlEntryPointIsClickable() {
        composeRule.onNodeWithTag("nav_self_control", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("self_control_usage").performClick()
        composeRule.onNodeWithTag("usage_guard_settings_list").assertExists()
    }
}
