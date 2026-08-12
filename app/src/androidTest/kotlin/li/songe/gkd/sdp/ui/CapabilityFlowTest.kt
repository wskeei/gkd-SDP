package li.songe.gkd.sdp.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.SdpUiTestHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun capabilityCenterOpensFromHomeAction() {
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.overview_work_mode_content_description),
        ).performClick()
        composeRule.onNodeWithTag("capability_center_total").assertExists()
    }
}
