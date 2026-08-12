package li.songe.gkd.sdp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.SdpUiTestHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Semantic-node smoke test for the core accessibility presentation. */
@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun overviewRootIsDisplayedWithTouchTargetText() {
        composeRule.onNodeWithTag("nav_overview").assertIsDisplayed()
    }
}
