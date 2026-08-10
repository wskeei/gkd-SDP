package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.navigation.AppDestination
import li.songe.gkd.sdp.navigation.toNavKey
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @Test
    fun everySemanticDestinationMapsToANavigationKey() {
        AppDestination.entries.forEach { destination ->
            assertTrue(destination.name, destination.toNavKey() != null)
        }
    }
}
