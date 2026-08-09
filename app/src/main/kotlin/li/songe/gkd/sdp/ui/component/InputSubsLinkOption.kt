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
            toast("非法链接")
            return
        }
        val initValue = initValueFlow.value
        if (initValue.isNotEmpty() && initValue == value) {
            toast("未修改")
            resume(null)
            return
        }
        if (subsItemsFlow.value.any { it.updateUrl == value }) {
            toast("已有相同链接订阅")
            return
        }
        val cleartextOrigin = CleartextOriginPolicy.canonicalOrigin(value)
        if (cleartextOrigin != null && cleartextOrigin !in CleartextOriginAuthorizations.originsFlow.value) {
            if (!authorizeCleartext) {
                toast("请先确认此明文来源")
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
                        Text(text = if (initValue.isNotEmpty()) "修改订阅" else "添加订阅")
                        PerfIconButton(
                            imageVector = PerfIcon.HelpOutline,
                            contentDescription = "订阅帮助",
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
                                Text(text = "请输入订阅链接")
                            },
                            isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                        )
                        if (needsCleartextAuthorization) {
                            Text(
                                text = "明文来源：$cleartextOrigin\nHTTP 内容可能在传输中被读取或修改；授权仅适用于此 scheme、host 与 port。",
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
                        Text(text = if (needsCleartextAuthorization) "仅授权此来源" else "确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = "取消")
                    }
                },
            )
        }
    }
}
