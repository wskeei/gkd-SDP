package li.songe.gkd.sdp.ui.share

import androidx.compose.runtime.staticCompositionLocalOf
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.performance.AppDrawReporter

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalIsTalkbackEnabled = staticCompositionLocalOf {
    false
}

val LocalDrawReporter = staticCompositionLocalOf<AppDrawReporter> {
    AppDrawReporter { }
}
