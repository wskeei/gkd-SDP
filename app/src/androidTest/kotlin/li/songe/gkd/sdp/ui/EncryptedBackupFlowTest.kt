package li.songe.gkd.sdp.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.SdpUiTestHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SdpUiTestHostActivity>()

    @Test
    fun privacyDataBackupEntryOpensFromSettingsSearch() {
        composeRule.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search_field", useUnmergedTree = true).performTextInput("备份")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_privacy_title),
        ).performClick()
        composeRule.onNodeWithTag("privacy_data_title").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("privacy_backup_action").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("privacy_backup_action").performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.backup_export_entry),
        ).assertExists()
    }
}
