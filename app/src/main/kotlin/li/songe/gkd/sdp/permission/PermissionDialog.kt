package li.songe.gkd.sdp.permission

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.util.stopCoroutine
import androidx.compose.ui.res.stringResource

data class AuthReason(
    val text: () -> String,
    val confirm: ((Activity) -> Unit)? = null,
)

@Composable
fun AuthDialog(authReasonFlow: MutableStateFlow<AuthReason?>) {
    val authAction = authReasonFlow.collectAsStateWithLifecycle().value
    val context = LocalActivity.current as MainActivity
    if (authAction != null) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.s_bef597b206))
            },
            text = {
                Text(text = authAction.text())
            },
            onDismissRequest = { authReasonFlow.value = null },
            confirmButton = {
                TextButton(onClick = {
                    authReasonFlow.value = null
                    authAction.confirm?.invoke(context)
                }) {
                    Text(text = stringResource(R.string.s_b56d9ac6c5))
                }
            },
            dismissButton = {
                TextButton(onClick = { authReasonFlow.value = null }) {
                    Text(text = stringResource(R.string.s_4d0b4688c7))
                }
            }
        )
    }
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val doNotAskAgain: Boolean) : PermissionResult()
}

suspend fun requiredPermission(
    context: MainActivity,
    permissionState: PermissionState
) {
    if (permissionState.updateAndGet()) return
    val result = permissionState.request?.invoke(context)
    if (result == null) {
        context.mainVm.authReasonFlow.value = permissionState.reason
        stopCoroutine()
    } else if (result is PermissionResult.Denied) {
        if (result.doNotAskAgain) {
            context.mainVm.authReasonFlow.value = permissionState.reason
        }
        stopCoroutine()
    }
}
