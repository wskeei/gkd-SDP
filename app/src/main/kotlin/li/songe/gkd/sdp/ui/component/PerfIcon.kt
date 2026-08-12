package li.songe.gkd.sdp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import li.songe.gkd.sdp.R

@Composable
fun PerfIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) = Icon(
    imageVector = imageVector,
    modifier = modifier,
    contentDescription = contentDescription ?: iconDefaultDescription(imageVector),
    tint = tint
)

@Composable
fun PerfIconButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    contentDescription: String? = null,
    onClickLabel: String? = null,
) = TooltipIconButtonBox(
    contentDescription = contentDescription ?: iconDefaultDescription(imageVector),
) {
    IconButton(
        modifier = modifier.semantics {
            if (onClickLabel != null) {
                this.onClick(label = onClickLabel, action = null)
            }
        },
        enabled = enabled,
        onClick = onClick,
        colors = colors,
    ) {
        PerfIcon(
            imageVector = imageVector,
            contentDescription = contentDescription ?: iconDefaultDescription(imageVector),
        )
    }
}

@Composable
fun PerfIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) = Icon(
    painter = painterResource(id),
    modifier = modifier,
    contentDescription = contentDescription,
    tint = tint
)

@Composable
fun PerfIconButton(
    @DrawableRes id: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    contentDescription: String? = null,
    onClickLabel: String? = null,
) = TooltipIconButtonBox(
    contentDescription = contentDescription,
) {
    IconButton(
        modifier = modifier.semantics {
            if (onClickLabel != null) {
                this.onClick(label = onClickLabel, action = null)
            }
        },
        enabled = enabled,
        onClick = onClick,
        colors = colors,
    ) {
        PerfIcon(
            id = id,
            contentDescription = contentDescription,
        )
    }
}

@StringRes
fun getIconDefaultDescRes(imageVector: ImageVector): Int? = when (imageVector) {
    PerfIcon.Add -> R.string.perf_icon_add
    PerfIcon.Edit -> R.string.perf_icon_edit
    PerfIcon.Save -> R.string.perf_icon_save
    PerfIcon.Delete -> R.string.perf_icon_delete
    PerfIcon.Share -> R.string.perf_icon_share
    PerfIcon.Settings -> R.string.perf_icon_settings
    PerfIcon.Close -> R.string.perf_icon_close
    PerfIcon.ArrowBack -> R.string.perf_icon_back
    PerfIcon.HelpOutline -> R.string.perf_icon_help
    PerfIcon.ToggleOff -> R.string.perf_icon_toggle_off
    PerfIcon.ToggleOn -> R.string.perf_icon_toggle_on
    PerfIcon.History -> R.string.perf_icon_history
    PerfIcon.Sort -> R.string.perf_icon_sort
    PerfIcon.OpenInNew -> R.string.perf_icon_open_in_new
    PerfIcon.ContentCopy -> R.string.perf_icon_copy
    PerfIcon.MoreVert -> R.string.perf_icon_more
    else -> null
}

@Composable
private fun iconDefaultDescription(imageVector: ImageVector): String? =
    getIconDefaultDescRes(imageVector)?.let { stringResource(it) }

object PerfIcon {
    val Block get() = Icons.Default.Block
    val History get() = Icons.Default.History
    val Sort get() = Icons.AutoMirrored.Filled.Sort
    val Add get() = Icons.Outlined.Add
    val KeyboardArrowRight get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val ContentCopy get() = Icons.Outlined.ContentCopy
    val MoreVert get() = Icons.Default.MoreVert
    val ArrowBack get() = Icons.AutoMirrored.Filled.ArrowBack
    val Android get() = Icons.Default.Android
    val Edit get() = Icons.Outlined.Edit
    val Save get() = Icons.Outlined.Save
    val Share get() = Icons.Default.Share
    val Delete get() = Icons.Outlined.Delete
    val Eco get() = Icons.Outlined.Eco
    val Close get() = Icons.Default.Close
    val OpenInNew get() = Icons.AutoMirrored.Outlined.OpenInNew
    val Settings get() = Icons.Outlined.Settings
    val Home get() = Icons.Outlined.Home
    val FormatListBulleted get() = Icons.AutoMirrored.Filled.FormatListBulleted
    val Apps get() = Icons.Default.Apps
    val Info get() = Icons.Outlined.Info
    val ToggleOff get() = Icons.Outlined.ToggleOff
    val ToggleOn get() = Icons.Outlined.ToggleOn
    val HelpOutline get() = Icons.AutoMirrored.Outlined.HelpOutline
    val ArrowForward get() = Icons.AutoMirrored.Filled.ArrowForward
    val Image get() = Icons.Outlined.Image
    val WarningAmber get() = Icons.Default.WarningAmber
    val RocketLaunch get() = Icons.Outlined.RocketLaunch
    val CenterFocusWeak get() = Icons.Default.CenterFocusWeak
    val AutoMode get() = Icons.Outlined.AutoMode
    val LightMode get() = Icons.Outlined.LightMode
    val DarkMode get() = Icons.Outlined.DarkMode
    val VerifiedUser get() = Icons.Outlined.VerifiedUser
    val Api get() = Icons.Outlined.Api
    val Autorenew get() = Icons.Default.Autorenew
    val UnfoldMore get() = Icons.Default.UnfoldMore
    val Memory get() = Icons.Default.Memory
    val Notifications get() = Icons.Outlined.Notifications
    val Layers get() = Icons.Outlined.Layers
    val Equalizer get() = Icons.Outlined.Equalizer
    val Lock get() = Icons.Outlined.Lock
    val Mindful get() = Icons.Outlined.SelfImprovement
    val Title get() = Icons.Outlined.Title
    val TextFields get() = Icons.Outlined.TextFields
    val ArrowDownward get() = Icons.Outlined.ArrowDownward
    val Check get() = Icons.Outlined.Check
    val Search get() = Icons.Outlined.Search
    val Download get() = Icons.Outlined.Download
    val Schedule get() = Icons.Outlined.Schedule
    val CheckBox get() = Icons.Outlined.CheckBox
    val CheckBoxOutlineBlank get() = Icons.Outlined.CheckBoxOutlineBlank
}
