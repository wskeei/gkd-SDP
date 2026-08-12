package li.songe.gkd.sdp.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.remote.CleartextOriginAuthorizations
import li.songe.gkd.sdp.remote.CleartextOriginPolicy
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import kotlin.coroutines.resume
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R


class InputSubsLinkOption {
    private val showFlow = MutableStateFlow(false)
    private val valueFlow = MutableStateFlow("")
    private val initValueFlow = MutableStateFlow("")
    private var continuation: CancellableContinuation<String?>? = null

    private fun resume(value: String?) {
        showFlow.value = false
        valueFlow.value = ""
        initValueFlow.value = ""
        if (continuation?.isActive == true) {
            continuation?.resume(value)
        }
        continuation = null
    }

    private fun submit(authorizeCleartext: Boolean) {
        val value = valueFlow.value
        if (!URLUtil.isNetworkUrl(value)) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_e7e0ffcd50))
            return
        }
        val initValue = initValueFlow.value
        if (initValue.isNotEmpty() && initValue == value) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_fff8cc4d94))
            resume(null)
            return
        }
        if (subsItemsFlow.value.any { it.updateUrl == value }) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_d41dda6f65))
            return
        }
        val cleartextOrigin = CleartextOriginPolicy.canonicalOrigin(value)
        if (cleartextOrigin != null && cleartextOrigin !in CleartextOriginAuthorizations.originsFlow.value) {
            if (!authorizeCleartext) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_34ba6b190f))
                return
            }
            CleartextOriginAuthorizations.authorize(value)
        }
        resume(value)
    }

    private fun cancel() = resume(null)

    suspend fun getResult(initValue: String = ""): String? {
        initValueFlow.value = initValue
        valueFlow.value = initValue
        showFlow.value = true
        return suspendCancellableCoroutine {
            continuation = it
        }
    }

    @Composable
    fun ContentDialog() {
        val show by showFlow.collectAsStateWithLifecycle()
        if (show) {
            val mainVm = LocalMainViewModel.current
            val value by valueFlow.collectAsStateWithLifecycle()
            val initValue by initValueFlow.collectAsStateWithLifecycle()
            val authorizedOrigins by CleartextOriginAuthorizations.originsFlow.collectAsStateWithLifecycle()
            val cleartextOrigin = CleartextOriginPolicy.canonicalOrigin(value)
            val needsCleartextAuthorization = cleartextOrigin != null &&
                cleartextOrigin !in authorizedOrigins
            AlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (initValue.isNotEmpty()) stringResource(R.string.s_1508e32d35) else stringResource(R.string.s_6debaa8885))
                        PerfIconButton(
                            imageVector = PerfIcon.HelpOutline,
                            contentDescription = stringResource(R.string.subs_help),
                            onClick = throttle {
                                cancel()
                                mainVm.navigatePage(WebViewRoute(initUrl = ShortUrlSet.URL5))
                            })
                    }
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                valueFlow.value = it.trim()
                            },
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .autoFocus(),
                            placeholder = {
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_a00626547a))
                            },
                            isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                        )
                        if (needsCleartextAuthorization) {
                            Text(
                                text = stringResource(R.string.s_ac96d3416e, (cleartextOrigin).toString()),
                            )
                        }
                    }
                },
                onDismissRequest = {
                    cancel()
                },
                confirmButton = {
                    TextButton(
                        enabled = value.isNotEmpty(),
                        onClick = throttle(fn = {
                            submit(authorizeCleartext = needsCleartextAuthorization)
                        }),
                    ) {
                        Text(text = if (needsCleartextAuthorization) stringResource(R.string.s_62221c94a0) else stringResource(R.string.s_f526c89937))
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = stringResource(R.string.s_4d0b4688c7))
                    }
                },
            )
        }
    }
}
