package li.songe.gkd.sdp.ui.style

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * Fixed motion durations and easings.
 *
 * Durations are scaled by the system animator duration scale; when the
 * system "remove animations" setting is on the scale is 0 and every
 * non-essential animation runs in 0ms.
 */
object MotionTokens {
    /** Micro interactions: checkmarks, inline feedback. */
    const val DurationMicroMs = 120

    /** Page and theme transitions. */
    const val DurationPageThemeMs = 180

    /** Emphasis transitions: dialogs, expanding content. */
    const val DurationEmphasisMs = 240

    val FastOutSlowIn = FastOutSlowInEasing
    val Linear = LinearEasing

    fun scaledDurationMs(durationMs: Int, scaleFactor: Float): Int =
        (durationMs * scaleFactor.coerceIn(0f, 1f)).roundToInt()
}

/**
 * Read the current system animator duration scale and apply it to
 * [durationMs]. The system "remove animations" setting reports 0.
 */
@Composable
fun animatableDurationMs(durationMs: Int): Int {
    val context = LocalContext.current
    val scale = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    return MotionTokens.scaledDurationMs(durationMs, scale)
}
