package li.songe.gkd.sdp.ui.style

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Fixed type scale. Line heights are per-pair so the scale survives user font
 * scaling; body text never gets maxLines=1.
 */
object TypographyTokens {
    val DisplaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp)
    val HeadlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp)
    val TitleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
    val TitleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val BodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val BodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val LabelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
}
