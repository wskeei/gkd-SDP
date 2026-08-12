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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import li.songe.gkd.sdp.R

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
                error(li.songe.gkd.sdp.app.getString(R.string.upgrade_network_unavailable))
            }
            val beta = storeFlow.value.updateChannel == UpdateChannelOption.Beta.value
            val newVersion = GitHubReleaseUpdateSource.fetchLatest(client, beta)
            if (newVersion == null || !GitHubReleaseUpdateSource.isNewer(newVersion, META.versionCode)) {
                if (manual) toast(li.songe.gkd.sdp.app.getString(R.string.s_f0ece473ea))
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
                        li.songe.gkd.sdp.app.getString(
                            R.string.upgrade_download_failed_http,
                            response.status.value,
                        )
                    }
                }.bodyAsChannel()
                try {
                    partialFile.outputStream().use { output ->
                        while (!channel.isClosedForRead) {
                            val count = channel.readAvailable(buffer, 0, buffer.size)
                            if (count == -1) break
                            if (count == 0) continue
                            require(bytesReceived + count <= newVersion.fileSize) {
                                li.songe.gkd.sdp.app.getString(R.string.upgrade_download_too_large)
                            }
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
                    li.songe.gkd.sdp.app.getString(
                        R.string.upgrade_download_size_mismatch,
                        bytesReceived,
                        newVersion.fileSize,
                    )
                }
                val actualSha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                require(actualSha256.equals(newVersion.sha256, ignoreCase = true)) {
                    li.songe.gkd.sdp.app.getString(R.string.upgrade_download_sha_mismatch)
                }
                check(partialFile.renameTo(apkFile)) {
                    li.songe.gkd.sdp.app.getString(R.string.upgrade_download_save_failed)
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
        newVersionFlow.collectAsStateWithLifecycle().value?.let { newVersionVal ->
            val text = remember(newVersionVal) {
                val logs = newVersionVal.versionLogs.takeWhile { v ->
                    v.code > META.versionCode
                }
                val changelog = if (logs.isNotEmpty()) {
                    if (logs.size > 1) {
                        logs.joinToString("\n\n") { v -> "v${v.name}\n${v.desc}" }
                    } else {
                        logs.first().desc
                    }
                } else {
                    newVersionVal.changelog
                }
                "v${META.versionName} -> v${newVersionVal.versionName}\n\n${
                    changelog
                }".trimEnd()
            }
            AlertDialog(
                title = {
                    Text(text = li.songe.gkd.sdp.app.getString(R.string.s_b0b9270849))
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
                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_c1f18f4e0a))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { newVersionFlow.value = null }) {
                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_4d0b4688c7))
                    }
                    if (!lastManual) {
                        TextButton(onClick = {
                            newVersionFlow.value = null
                            ignoreVersionListFlow.update {
                                it + newVersionVal.versionCode
                            }
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_d1dcaf9ad6))
                        }) {
                            Text(text = li.songe.gkd.sdp.app.getString(R.string.s_d84129b8be))
                        }
                    }
                },
            )
        }

        downloadStatusFlow.collectAsStateWithLifecycle().value?.let { downloadStatusVal ->
            when (downloadStatusVal) {
                is LoadStatus.Loading -> {
                    AlertDialog(
                        title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_327d59b5bd)) },
                        text = {
                            LinearProgressIndicator(
                                progress = { downloadStatusVal.progress },
                            )
                        },
                        onDismissRequest = {},
                        confirmButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = LoadStatus.Failure(
                                    // i18n-ignore: legacy fallback or non-display heuristic data
                                    Exception("终止下载")
                                )
                                downloadJob?.cancel()
                            }) {
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_20bf3bc4ef))
                            }
                        },
                    )
                }

                is LoadStatus.Failure -> {
                    AlertDialog(
                        title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e0dab22b1a)) },
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
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_6c14bd7f6f))
                            }
                        },
                    )
                }

                is LoadStatus.Success -> {
                    AlertDialog(
                        title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_9edcdf6586)) },
                        text = {
                            Text(text = li.songe.gkd.sdp.app.getString(R.string.s_12abdcba31))
                        },
                        onDismissRequest = {},
                        dismissButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = null
                            }) {
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_6c14bd7f6f))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = throttle {
                                installApk(downloadStatusVal.result)
                            }) {
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_087db63ab1))
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
