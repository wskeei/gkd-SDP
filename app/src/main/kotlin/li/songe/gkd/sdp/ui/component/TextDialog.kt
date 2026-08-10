package li.songe.gkd.sdp.ui.component

import android.webkit.URLUtil
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.util.openUri
import li.songe.gkd.sdp.util.throttle
import androidx.compose.ui.res.stringResource

@Composable
fun TextDialog(
    textFlow: MutableStateFlow<String?>
) {
    val text = textFlow.collectAsStateWithLifecycle().value
    if (text != null) {
        val isUri = remember(text) { URLUtil.isNetworkUrl(text) }
        val onDismissRequest = {
            textFlow.value = null
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = if (isUri) stringResource(R.string.s_a8d5390010) else stringResource(R.string.s_cd05c7b50f))
            },
            text = {
                CopyTextCard(text = text)
            },
            confirmButton = {
                if (isUri) {
                    TextButton(onClick = throttle {
                        onDismissRequest()
                        openUri(text)
                    }) {
                        Text(text = stringResource(R.string.s_65fc81e161))
                    }
                } else {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.s_6c14bd7f6f))
                    }
                }
            },
        )
    }
}
