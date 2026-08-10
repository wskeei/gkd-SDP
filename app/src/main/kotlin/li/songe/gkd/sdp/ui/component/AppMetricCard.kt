package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.surfaceCardColors

/** A labeled summary metric with an optional trend/delta line. */
@Composable
fun AppMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    deltaText: String? = null,
    deltaColor: androidx.compose.ui.graphics.Color? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = surfaceCardColors,
        shape = CardDefaults.shape,
    ) {
        Column(modifier = Modifier.padding(DimensionTokens.SpacingBase)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (deltaText != null) {
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = deltaColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
