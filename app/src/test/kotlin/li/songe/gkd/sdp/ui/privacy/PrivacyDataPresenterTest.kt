package li.songe.gkd.sdp.ui.privacy

import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDataPresenterTest {
    @Test
    fun everyCategoryIsPresentedWithStableOrderAndSafeSummary() {
        val inventory = DataCategory.entries.associateWith {
            DataDeletionCoordinator.CategoryStatus(
                recordCount = if (it == DataCategory.ALL_APP_DATA) 10L else 1L,
                bytes = 1024L,
            )
        }

        val ui = PrivacyDataPresenter.present(inventory)

        assertEquals(DataCategory.entries.toList(), ui.map { it.category })
        assertTrue(ui.all { it.summary.isNotBlank() })
        assertTrue(ui.none { it.summary.contains("reasonText") || it.summary.contains("http") })
    }

    @Test
    fun configurationAndAllDataAreNotDirectlyDeletableFromInventory() {
        val inventory = DataCategory.entries.associateWith {
            DataDeletionCoordinator.CategoryStatus(recordCount = 0L, bytes = 0L)
        }

        val ui = PrivacyDataPresenter.present(inventory)

        assertFalse(ui.first { it.category == DataCategory.SUBSCRIPTIONS_RULES_CONFIG }.deletable)
        assertFalse(ui.first { it.category == DataCategory.SELF_CONTROL_CONFIG }.deletable)
        assertFalse(ui.first { it.category == DataCategory.ALL_APP_DATA }.deletable)
        assertTrue(ui.first { it.category == DataCategory.USAGE_REQUEST_HISTORY }.deletable)
    }

    @Test
    fun activeSessionBlocksConfigurationCategories() {
        val active = DataDeletionCoordinator.CategoryStatus(
            recordCount = 1L,
            bytes = 0L,
            hasActiveSession = true,
        )
        val ui = PrivacyDataPresenter.present(
            mapOf(
                DataCategory.SELF_CONTROL_CONFIG to active,
            ),
        )

        val item = ui.first { it.category == DataCategory.SELF_CONTROL_CONFIG }
        assertFalse(item.deletable)
        assertTrue(item.blockReason != null)
    }
}
