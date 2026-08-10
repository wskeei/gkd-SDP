package li.songe.gkd.sdp.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.data.GithubPoliciesAsset
import li.songe.gkd.sdp.util.GithubCookieException
import li.songe.gkd.sdp.util.LoadStatus
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.uploadFileToGithub
import java.io.File
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

class UploadOptions(
    private val mainVm: MainViewModel,
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(
        cookie: String,
        getFile: suspend () -> File,
        onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)?
    ) = mainVm.viewModelScope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val policiesAsset = uploadFileToGithub(cookie, getFile()) {
                if (statusFlow.value is LoadStatus.Loading) {
                    statusFlow.value = LoadStatus.Loading(it)
                }
            }
            statusFlow.value = LoadStatus.Success(policiesAsset)
            onSuccessResult?.invoke(policiesAsset)
        } catch (e: Exception) {
            LogUtils.d(e)
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }


    private var showHref: (GithubPoliciesAsset) -> String = { it.shortHref }
    fun startTask(
        getFile: suspend () -> File,
        showHref: (GithubPoliciesAsset) -> String = { it.shortHref },
        onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)? = null
    ) {
        val cookie = mainVm.githubCookieFlow.value
        if (cookie.isEmpty()) {
            toast(app.getString(R.string.s_7f96c93415))
            mainVm.showEditCookieDlgFlow.value = true
            return
        }
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        this.showHref = showHref
        job = buildTask(cookie, getFile, onSuccessResult)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel("上传已取消")
            job = null
        }
    }


    @Composable
    fun ShowDialog() {
        when (val status = statusFlow.collectAsStateWithLifecycle().value) {
            null -> {}
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = app.getString(R.string.s_3219dbb398)) },
                    text = {
                        val showExactProgress = 0f < status.progress && status.progress < 1f
                        AnimatedContent(showExactProgress) { showExact ->
                            if (showExact) {
                                LinearProgressIndicator(
                                    progress = { status.progress },
                                )
                            } else {
                                LinearProgressIndicator()
                            }
                        }
                    },
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            stopTask()
                        }) {
                            Text(text = app.getString(R.string.s_b387756d31))
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                val href = showHref(status.result)
                AlertDialog(
                    title = { Text(text = stringResource(R.string.s_95bb0f726c)) },
                    text = { CopyTextCard(text = href) },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = stringResource(R.string.s_6c14bd7f6f))
                        }
                    }
                )
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = stringResource(R.string.s_a6f805694b)) },
                    text = {
                        Text(text = status.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { statusFlow.value = null },
                    dismissButton = if (status.exception is GithubCookieException) ({
                        TextButton(onClick = {
                            statusFlow.value = null
                            mainVm.showEditCookieDlgFlow.value = true
                        }) {
                            Text(text = stringResource(R.string.s_f5d0d9c7f0))
                        }
                    }) else {
                        null
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = stringResource(R.string.s_6c14bd7f6f))
                        }
                    },
                )
            }
        }
    }
}
