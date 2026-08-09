package li.songe.gkd.sdp.navigation

import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.home.HomeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `home tab is the persisted root and removes stale detail routes`() {
        val navigator = AppNavigator()
        navigator.navigate(WebViewRoute("https://gkd.li"))

        navigator.navigateHome(3)

        assertEquals(1, navigator.backStack.size)
        assertEquals(HomeRoute(3), navigator.backStack.single())
    }

    @Test
    fun `repeated home destination replaces the same root`() {
        val navigator = AppNavigator()

        navigator.navigate(AppDestination.SETTINGS)
        navigator.navigate(AppDestination.SETTINGS)

        assertEquals(1, navigator.backStack.size)
        assertTrue(navigator.backStack.single() is HomeRoute)
        assertEquals(3, (navigator.backStack.single() as HomeRoute).tabKey)
    }
}
