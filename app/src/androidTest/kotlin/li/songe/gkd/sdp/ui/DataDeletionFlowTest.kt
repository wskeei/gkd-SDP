package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataDeletionFlowTest {
    @Test
    fun activeSessionBlocksConfigurationDeletionOnly() {
        val active = DataDeletionCoordinator.CategoryStatus(
            recordCount = 1L,
            bytes = 0L,
            hasActiveSession = true,
        )

        assertTrue(
            DataDeletionCoordinator.deletionBlocked(
                DataCategory.SELF_CONTROL_CONFIG,
                active,
            ),
        )
        assertFalse(
            DataDeletionCoordinator.deletionBlocked(
                DataCategory.USAGE_REQUEST_HISTORY,
                active,
            ),
        )
    }
}
