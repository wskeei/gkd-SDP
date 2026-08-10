package li.songe.gkd.sdp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import li.songe.gkd.sdp.ui.home.BottomNavItem
import li.songe.gkd.sdp.ui.home.HomeRoute

/** Navigation boundary shared by the activity and non-UI entry points. */
class AppNavigator {
    private var boundBackStack: NavBackStack<NavKey> = NavBackStack(HomeRoute())
    private val _navigationEffects = MutableSharedFlow<AppDestination>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val navigationEffects: SharedFlow<AppDestination> = _navigationEffects.asSharedFlow()

    val backStack: NavBackStack<NavKey> get() = boundBackStack

    fun bindBackStack(backStack: NavBackStack<NavKey>) {
        boundBackStack = backStack
    }

    fun navigateHome(tabKey: Int) {
        if (boundBackStack.isEmpty()) {
            boundBackStack.add(HomeRoute(tabKey))
            return
        }
        boundBackStack[0] = HomeRoute(tabKey)
        while (boundBackStack.size > 1) {
            boundBackStack.removeAt(boundBackStack.lastIndex)
        }
    }

    fun pop(): Boolean {
        if (boundBackStack.size <= 1) return false
        boundBackStack.removeAt(boundBackStack.lastIndex)
        return true
    }

    fun navigate(key: NavKey, replace: Boolean = false) {
        if (boundBackStack.lastOrNull() == key) return
        if (replace) {
            boundBackStack[boundBackStack.lastIndex] = key
        } else {
            boundBackStack.add(key)
        }
    }

    fun navigate(destination: AppDestination) {
        val key = destination.toNavKey()
        when (destination) {
            AppDestination.OVERVIEW,
            AppDestination.RULES_SUBSCRIPTIONS,
            AppDestination.RULES_APPS,
            AppDestination.SETTINGS,
            -> navigateHome(tabFor(destination)!!.key)

            else -> navigate(key)
        }
        _navigationEffects.tryEmit(destination)
    }

    fun tabFor(destination: AppDestination): BottomNavItem? = when (destination) {
        AppDestination.OVERVIEW -> BottomNavItem.Control
        AppDestination.RULES_SUBSCRIPTIONS -> BottomNavItem.SubsManage
        AppDestination.RULES_APPS -> BottomNavItem.AppList
        AppDestination.SETTINGS -> BottomNavItem.Settings

        else -> null
    }
}

/** Process-local hand-off for permission callbacks created before the Activity exists. */
object AppNavigationRequests {
    private val _flow = MutableSharedFlow<AppDestination>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val flow: SharedFlow<AppDestination> = _flow.asSharedFlow()

    fun request(destination: AppDestination) {
        _flow.tryEmit(destination)
    }
}
