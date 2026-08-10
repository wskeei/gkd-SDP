package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens

/** Typed inline message levels; never conveyed by color alone. */
enum class InlineMessageKind {
    Info,
    Success,
    Warning,
    Error,
}

private fun InlineMessageKind.icon(): ImageVector = when (this) {
    InlineMessageKind.Info -> Icons.Outlined.Info
    InlineMessageKind.Success -> Icons.Outlined.CheckCircle
    InlineMessageKind.Warning -> Icons.Outlined.WarningAmber
    InlineMessageKind.Error -> Icons.Outlined.ErrorOutline
}

@Composable
private fun InlineMessageKind.tint(): Color = when (this) {
    InlineMessageKind.Info -> MaterialTheme.colorScheme.primary
    InlineMessageKind.Success -> MaterialTheme.colorScheme.secondary
    InlineMessageKind.Warning -> MaterialTheme.colorScheme.tertiary
    InlineMessageKind.Error -> MaterialTheme.colorScheme.error
}

/** An inline message with an icon, a title and optional body text. */
@Composable
fun InlineMessage(
    kind: InlineMessageKind,
    title: String,
    text: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DimensionTokens.SpacingBase, vertical = DimensionTokens.SpacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = null,
            tint = kind.tint(),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(DimensionTokens.IconSizeDefault),
        )
        Spacer(modifier = Modifier.width(DimensionTokens.SpacingSm))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
