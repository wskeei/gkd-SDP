package li.songe.gkd.sdp.ui.style

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LocalMotionDurationScale
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * Fixed motion durations and easings.
 *
 * Durations are scaled by the system motion preference through
 * [LocalMotionDurationScale]; when the system "remove animations" setting is
 * on the scale factor is 0 and every non-essential animation runs in 0ms.
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

    fun themeTransition(scaleFactor: Float) = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = scaledDurationMs(DurationPageThemeMs, scaleFactor),
        easing = FastOutSlowIn,
    )
}

/** Read the current system motion scale and apply it to [durationMs]. */
@Composable
fun animatableDurationMs(durationMs: Int): Int =
    MotionTokens.scaledDurationMs(durationMs, LocalMotionDurationScale.current.scaleFactor)
