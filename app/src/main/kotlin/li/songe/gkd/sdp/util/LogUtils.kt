package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.loc.Loc

/**
 * Compatibility facade for older call sites.
 *
 * Argument values are deliberately never serialized. The typed diagnostic layer records
 * only the call-site hash, argument count, outcome, and error category. New code should call
 * [DiagnosticLogger.record] with a [li.songe.gkd.sdp.diagnostics.DiagnosticEvent].
 */
object LogUtils {
    fun d(
        vararg args: Any?,
        @Loc loc: String = "",
        @Loc("{fileName}") fileName: String = "",
        @Suppress("UNUSED_PARAMETER") tag: String = fileName.substringBeforeLast('.'),
    ) {
        DiagnosticLogger.recordLegacy(
            arguments = args.asList(),
            location = loc,
            fileName = fileName,
        )
    }
}
