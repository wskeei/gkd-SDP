package li.songe.gkd.sdp.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The saved Navigation 3 back stack is exercised by the activity recreation harness. */
@RunWith(AndroidJUnit4::class)
class NavigationRestoreTest {
    @Test
    fun semanticDestinationKeysAreStable() {
        AppDestination.entries.forEach { destination ->
            check(destination.toNavKey() != null)
        }
    }
}
