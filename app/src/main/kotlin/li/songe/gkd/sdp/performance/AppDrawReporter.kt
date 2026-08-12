package li.songe.gkd.sdp.performance

import androidx.compose.runtime.Stable

/** Reports the first interactive content once per Activity instance. */
@Stable
class AppDrawReporter(
    private val onReport: () -> Unit,
) {
    private var reported = false

    fun reportInteractiveContent() {
        if (!reported) {
            reported = true
            onReport()
        }
    }

    val hasReported: Boolean
        get() = reported
}
