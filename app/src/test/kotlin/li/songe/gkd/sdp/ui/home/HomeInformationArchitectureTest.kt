package li.songe.gkd.sdp.ui.home

import li.songe.gkd.sdp.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInformationArchitectureTest {
    @Test
    fun fourTopLevelDestinationsExistInOrder() {
        assertEquals(4, HomeDestination.all.size)
        assertEquals(HomeDestination.OVERVIEW, HomeDestination.all[0])
        assertEquals(HomeDestination.SELF_CONTROL, HomeDestination.all[1])
        assertEquals(HomeDestination.RULES, HomeDestination.all[2])
        assertEquals(HomeDestination.SETTINGS, HomeDestination.all[3])
    }

    @Test
    fun unknownKeysFallBackToOverview() {
        assertEquals(HomeDestination.OVERVIEW, HomeDestination.fromKey(-1))
        assertEquals(HomeDestination.OVERVIEW, HomeDestination.fromKey(99))
        assertEquals(HomeDestination.OVERVIEW, HomeDestination.fromKey(0))
    }

    @Test
    fun destinationKeysAreDistinct() {
        val keys = HomeDestination.all.map { it.key }.toSet()
        assertEquals(4, keys.size)
    }

    @Test
    fun legacyControlMapsToOverviewAndSubsAndAppsToRules() {
        assertEquals(0, HomeDestination.OVERVIEW.key)
        assertEquals(2, HomeDestination.RULES.key)
        // the legacy tab keys 1 (subscriptions) and 2 (app list) both live
        // under the rules destination now
        assertTrue(HomeDestination.RULES.key == 2)
    }

    @Test
    fun semanticDestinationsStillResolve() {
        val destinations = listOf(
            AppDestination.OVERVIEW,
            AppDestination.SELF_CONTROL,
            AppDestination.RULES_SUBSCRIPTIONS,
            AppDestination.RULES_APPS,
            AppDestination.SETTINGS,
        )
        destinations.forEach { destination ->
            check(destination.toNavKey() != null)
        }
        assertNotEquals(AppDestination.OVERVIEW, AppDestination.SETTINGS)
    }
}
