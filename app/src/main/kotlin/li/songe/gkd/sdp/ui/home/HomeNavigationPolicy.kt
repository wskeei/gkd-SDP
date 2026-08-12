package li.songe.gkd.sdp.ui.home

enum class HomeNavigationLayout {
    BOTTOM_BAR,
    RAIL,
    EXPANDED_RAIL,
}

enum class HomeClickAction {
    NAVIGATE,
    RESET_CURRENT,
}

object HomeNavigationPolicy {
    const val BOTTOM_BAR_MAX_WIDTH = 599
    const val RAIL_MIN_WIDTH = 600
    const val EXPANDED_MIN_WIDTH = 840

    fun layout(widthDp: Int): HomeNavigationLayout = when {
        widthDp <= BOTTOM_BAR_MAX_WIDTH -> HomeNavigationLayout.BOTTOM_BAR
        widthDp < EXPANDED_MIN_WIDTH -> HomeNavigationLayout.RAIL
        else -> HomeNavigationLayout.EXPANDED_RAIL
    }

    fun click(
        current: HomeDestination,
        clicked: HomeDestination,
    ): HomeClickAction = if (current == clicked) {
        HomeClickAction.RESET_CURRENT
    } else {
        HomeClickAction.NAVIGATE
    }
}
