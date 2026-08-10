package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import li.songe.gkd.sdp.ui.style.DimensionTokens

object AppFormFieldPolicy {
    /** "12/50" style counter, or null when no limit applies. */
    fun charCountLabel(currentLength: Int, maxLength: Int): String? =
        if (maxLength > 0) "$currentLength/$maxLength" else null

    /**
     * Validates the field: returns a stable error code ("field_required" /
     * "field_too_short") or null. The UI maps codes to user text; the code
     * itself is never shown raw.
     */
    fun validationError(value: String, required: Boolean, minLength: Int = 0): String? = when {
        required && value.isBlank() -> "field_required"
        minLength > 0 && value.length < minLength -> "field_too_short"
        else -> null
    }
}

/**
 * A labeled text field with supporting text, inline error, character counter,
 * keyboard type and IME action. The label stays permanently visible; the
 * placeholder never acts as the label.
 */
@Composable
fun AppFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    maxLength: Int = 0,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val counter = AppFormFieldPolicy.charCountLabel(value.length, maxLength)
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (maxLength <= 0 || it.length <= maxLength) onValueChange(it)
        },
        modifier = modifier,
        label = { Text(label) },
        supportingText = when {
            errorText != null -> ({ Text(errorText, color = androidx.compose.material3.MaterialTheme.colorScheme.error) })
            supportingText != null || counter != null -> ({
                Text(listOfNotNull(supportingText, counter).joinToString("  "))
            })
            else -> null
        },
        isError = errorText != null,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() },
        ),
        enabled = enabled,
    )
}
