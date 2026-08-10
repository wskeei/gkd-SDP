package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

object FullDeletionPolicy {
    /** Exact phrase required to confirm a full data wipe. */
    const val FullDeletionPhrase = "删除全部数据"

    fun isConfirmed(input: String): Boolean = input.trim() == FullDeletionPhrase
}

/**
 * Confirmation dialog. Destructive actions use the error color and name the
 * exact object and consequence; full data deletion additionally requires
 * typing [FullDeletionPolicy.FullDeletionPhrase].
 */
@Composable
fun AppConfirmationDialog(
    title: String,
    objectName: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    requiresPhrase: Boolean = false,
) {
    var phraseInput by remember { mutableStateOf("") }
    val phraseConfirmed = !requiresPhrase || FullDeletionPolicy.isConfirmed(phraseInput)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.s_1bbac91b90, (objectName).toString()))
                Spacer(modifier = Modifier.height(DimensionTokens.SpacingSm))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (requiresPhrase) {
                    Spacer(modifier = Modifier.height(DimensionTokens.SpacingBase))
                    AppFormField(
                        label = stringResource(R.string.s_aba4ea9027, (FullDeletionPolicy.FullDeletionPhrase).toString()),
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        supportingText = null,
                        errorText = if (phraseConfirmed || phraseInput.isBlank()) {
                            null
                        } else {
                            li.songe.gkd.sdp.app.getString(R.string.s_89c340d11d)
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                },
                enabled = phraseConfirmed,
            ) {
                Text(confirmText, textAlign = TextAlign.Center)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        },
    )
}
