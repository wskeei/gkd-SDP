package li.songe.gkd.sdp.util

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.store.createAnyFlow
import li.songe.gkd.sdp.store.storeFlow
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.days

private var lastCheckTime = 0L

class UpdateStatus(val scope: CoroutineScope) {
    private val checkUpdatingMutex = MutexState()
    val checkUpdatingFlow
        get() = checkUpdatingMutex.state
    private val newVersionFlow = MutableStateFlow<NewVersion?>(null)
    private val downloadStatusFlow = MutableStateFlow<LoadStatus<File>?>(null)
    private var downloadJob: Job? = null

    private val ignoreVersionListFlow by lazy {
        createAnyFlow(
            key = "ignore_version_list",
            default = { emptySet<Int>() },
            scope = scope,
        )
    }
    private var lastManual = false

    val canRecheck get() = System.currentTimeMillis() - lastCheckTime > 1.days.inWholeMilliseconds

    fun checkUpdate(manual: Boolean = false) = scope.launchTry(Dispatchers.IO, silent = !manual) {
        lastManual = manual
        checkUpdatingMutex.whenUnLock {
            lastCheckTime = System.currentTimeMillis()
            if (!NetworkUtils.isAvailable()) {
                error("网络不可用")
            }
            val beta = storeFlow.value.updateChannel == UpdateChannelOption.Beta.value
            val newVersion = GitHubReleaseUpdateSource.fetchLatest(client, beta)
            if (newVersion == null || !GitHubReleaseUpdateSource.isNewer(newVersion, META.versionCode)) {
                if (manual) toast("暂无更新")
                return@launchTry
            }
            if (!manual && ignoreVersionListFlow.value.contains(newVersion.versionCode)) return@launchTry
            newVersionFlow.value = newVersion
        }
    }.let { }

    private fun startDownload(newVersion: NewVersion) {
        if (downloadStatusFlow.value is LoadStatus.Loading) return
        downloadStatusFlow.value = LoadStatus.Loading(0f)
        GitHubReleaseUpdateSource.validateDownloadUrl(newVersion.downloadUrl, newVersion.releaseTag)
        val apkFile = sharedDir.resolve("gkd-sdp-v${newVersion.versionName}.apk").apply {
            if (exists()) {
                delete()
            }
        }
        val partialFile = sharedDir.resolve(".${apkFile.name}.part").apply {
            if (exists()) {
                delete()
            }
        }
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesReceived = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val channel = client.get(newVersion.downloadUrl) {
                }.also { response ->
                    require(response.status.value in 200..299) {
                        "下载文件请求失败：HTTP ${response.status.value}"
                    }
                }.bodyAsChannel()
                try {
                    partialFile.outputStream().use { output ->
                        while (!channel.isClosedForRead) {
                            val count = channel.readAvailable(buffer, 0, buffer.size)
                            if (count == -1) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            bytesReceived += count
                            if (downloadStatusFlow.value is LoadStatus.Loading) {
                                downloadStatusFlow.value = LoadStatus.Loading(
                                    (bytesReceived.toDouble() / newVersion.fileSize).toFloat().coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                } finally {
                    channel.cancel(null)
                }
                require(bytesReceived == newVersion.fileSize) {
                    "下载文件大小校验失败：${bytesReceived} != ${newVersion.fileSize}"
                }
                val actualSha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                require(actualSha256.equals(newVersion.sha256, ignoreCase = true)) {
                    "下载文件 SHA-256 校验失败"
                }
                check(partialFile.renameTo(apkFile)) {
                    "无法保存下载文件"
                }
                if (downloadStatusFlow.value is LoadStatus.Loading) {
                    downloadStatusFlow.value = LoadStatus.Success(apkFile)
                }
            } catch (e: Exception) {
                if (downloadStatusFlow.value is LoadStatus.Loading) {
                    downloadStatusFlow.value = LoadStatus.Failure(e)
                }
            } finally {
                if (partialFile.exists()) {
                    partialFile.delete()
                }
                downloadJob = null
            }
        }
    }

    @Composable
    fun UpgradeDialog() {
        newVersionFlow.collectAsState().value?.let { newVersionVal ->
            val text = remember {
                val logs = newVersionVal.versionLogs.takeWhile { v ->
                    v.code > META.versionCode
                }
                "v${META.versionName} -> v${newVersionVal.versionName}\n\n${
                    if (logs.size > 1) {
                        logs.joinToString("\n\n") { v -> "v${v.name}\n${v.desc}" }
                    } else if (logs.isNotEmpty()) {
                        logs.first().desc
                    } else {
                        ""
                    }
                }".trimEnd()
            }
            AlertDialog(
                title = {
                    Text(text = "新版本")
                },
                text = {
                    Text(
                        text = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                },
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        newVersionFlow.value = null
                        startDownload(newVersionVal)
                    }) {
                        Text(text = "下载更新")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { newVersionFlow.value = null }) {
                        Text(text = "取消")
                    }
                    if (!lastManual) {
                        TextButton(onClick = {
                            newVersionFlow.value = null
                            ignoreVersionListFlow.update {
                                it + newVersionVal.versionCode
                            }
                            toast("已忽略此版本")
                        }) {
                            Text(text = "忽略")
                        }
                    }
                },
            )
        }

        downloadStatusFlow.collectAsState().value?.let { downloadStatusVal ->
            when (downloadStatusVal) {
                is LoadStatus.Loading -> {
                    AlertDialog(
                        title = { Text(text = "下载中") },
                        text = {
                            LinearProgressIndicator(
                                progress = { downloadStatusVal.progress },
                            )
                        },
                        onDismissRequest = {},
                        confirmButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = LoadStatus.Failure(
                                    Exception("终止下载")
                                )
                                downloadJob?.cancel()
                            }) {
                                Text(text = "终止下载")
                            }
                        },
                    )
                }

                is LoadStatus.Failure -> {
                    AlertDialog(
                        title = { Text(text = "下载失败") },
                        text = {
                            Text(text = downloadStatusVal.exception.let {
                                it.message ?: it.toString()
                            })
                        },
                        onDismissRequest = { downloadStatusFlow.value = null },
                        confirmButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = null
                            }) {
                                Text(text = "关闭")
                            }
                        },
                    )
                }

                is LoadStatus.Success -> {
                    AlertDialog(
                        title = { Text(text = "下载完毕") },
                        text = {
                            Text(text = "可继续选择安装新版本")
                        },
                        onDismissRequest = {},
                        dismissButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = null
                            }) {
                                Text(text = "关闭")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = throttle {
                                installApk(downloadStatusVal.result)
                            }) {
                                Text(text = "安装")
                            }
                        })
                }
            }
        }
    }
}


private fun installApk(file: File) {
    val uri = FileProvider.getUriForFile(
        app,
        "${app.packageName}.provider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setDataAndType(uri, "application/vnd.android.package-archive")
    }
    app.tryStartActivity(intent)
}
