package li.songe.gkd.sdp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import li.songe.gkd.sdp.ui.AppBlockerPage
import li.songe.gkd.sdp.ui.AppBlockerRoute
import li.songe.gkd.sdp.ui.FocusLockPage
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.FocusModePage
import li.songe.gkd.sdp.ui.FocusModeRoute
import li.songe.gkd.sdp.ui.UsageGuardPage
import li.songe.gkd.sdp.ui.UsageGuardReviewPage
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.UrlBlockRoute
import li.songe.gkd.sdp.ui.UrlBlockerRoute
import li.songe.gkd.sdp.ui.capability.CapabilityCenterRoute
import li.songe.gkd.sdp.ui.capability.CapabilityCenterScreen
import li.songe.gkd.sdp.ui.home.HomePage
import li.songe.gkd.sdp.ui.home.HomeDestination
import li.songe.gkd.sdp.ui.home.HomeRoute
import li.songe.gkd.sdp.ui.privacy.PrivacyDataRoute
import li.songe.gkd.sdp.ui.privacy.PrivacyDataScreen
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.AppTheme

/** Debug-only instrumentation host that uses the same navigation root as MainActivity. */
class SdpUiTestHostActivity : ComponentActivity() {
    private val mainVm: MainViewModel by viewModels()

    fun setTab(destination: HomeDestination) {
        mainVm.handleClickDestination(destination)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = rememberNavBackStack(HomeRoute())
            mainVm.bindBackStack(backStack)
            CompositionLocalProvider(
                LocalMainViewModel provides mainVm,
            ) {
                AppTheme {
                    NavDisplay(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        backStack = backStack,
                        onBack = mainVm::popPage,
                        entryProvider = entryProvider {
                            entry<HomeRoute> { HomePage(it) }
                            entry<CapabilityCenterRoute> { CapabilityCenterScreen(mainVm) }
                            entry<PrivacyDataRoute> { PrivacyDataScreen() }
                            entry<UsageGuardRoute> { UsageGuardPage() }
                            entry<UsageGuardReviewRoute> { UsageGuardReviewPage() }
                            entry<FocusModeRoute> { FocusModePage() }
                            entry<AppBlockerRoute> { AppBlockerPage() }
                            entry<UrlBlockRoute> { UrlBlockerRoute() }
                            entry<FocusLockRoute> { FocusLockPage() }
                        },
                    )
                }
            }
        }
    }
}
