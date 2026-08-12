package li.songe.gkd.sdp.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationPolicyTest {
    @Test
    fun widthBucketsMatchInformationArchitecture() {
        assertEquals(HomeNavigationLayout.BOTTOM_BAR, HomeNavigationPolicy.layout(599))
        assertEquals(HomeNavigationLayout.RAIL, HomeNavigationPolicy.layout(600))
        assertEquals(HomeNavigationLayout.RAIL, HomeNavigationPolicy.layout(839))
        assertEquals(HomeNavigationLayout.EXPANDED_RAIL, HomeNavigationPolicy.layout(840))
    }

    @Test
    fun repeatedClickResetsWithoutNavigating() {
        assertEquals(
            HomeClickAction.RESET_CURRENT,
            HomeNavigationPolicy.click(HomeDestination.OVERVIEW, HomeDestination.OVERVIEW),
        )
        assertEquals(
            HomeClickAction.NAVIGATE,
            HomeNavigationPolicy.click(HomeDestination.OVERVIEW, HomeDestination.SETTINGS),
        )
    }
}
