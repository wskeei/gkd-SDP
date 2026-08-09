package li.songe.gkd.sdp.util

import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import kotlinx.serialization.decodeFromString
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.CrashData
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.diagnostics.SupportAppSummary
import li.songe.gkd.sdp.diagnostics.SupportBundleBuilder
import li.songe.gkd.sdp.diagnostics.SupportBundleMetadata
import li.songe.gkd.sdp.diagnostics.SupportBundleRequest
import li.songe.gkd.sdp.diagnostics.SupportCapabilitySummary
import li.songe.gkd.sdp.diagnostics.SupportCrashSummary
import li.songe.gkd.sdp.diagnostics.SupportDiagnosticEvent
import li.songe.gkd.sdp.permission.allPermissionStates
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.storeFlow
import java.io.File

fun File.autoMk(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

private val filesDir: File by lazy {
    val markFile = app.filesDir.resolve(".gkd")
    if (markFile.isFile) {
        app.filesDir
    } else {
        // fix #1333
        app.getExternalFilesDir(null) ?: app.filesDir.also {
            markFile.createNewFile()
        }
    }
}

val dbFolder: File
    get() = filesDir.resolve("db").autoMk()
val shFolder: File
    get() = filesDir.resolve("sh").autoMk()
val storeFolder: File
    get() = filesDir.resolve("store").autoMk()
val subsFolder: File
    get() = filesDir.resolve("subscription").autoMk()
val snapshotFolder: File
    get() = filesDir.resolve("snapshot").autoMk()
val logFolder: File
    get() = filesDir.resolve("log").autoMk()
val crashFolder: File
    get() = filesDir.resolve("crash").autoMk()
val crashTempFolder: File
    get() = filesDir.resolve("crash/temp").autoMk()

val privateStoreFolder: File
    get() = app.filesDir.resolve("private-store").autoMk()

private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }
val coilCacheDir: File
    get() = cacheDir.resolve("coil").autoMk()
val sharedDir: File
    get() = cacheDir.resolve("shared").autoMk()
private val tempDir: File
    get() = cacheDir.resolve("temp").autoMk()

fun createGkdTempDir(): File {
    return tempDir
        .resolve(System.currentTimeMillis().toString())
        .apply { mkdirs() }
}

private fun removeExpired(dir: File) {
    dir.listFiles()?.forEach { f ->
        if (System.currentTimeMillis() - f.lastModified() > DateUtils.HOUR_IN_MILLIS) {
            if (f.isDirectory) {
                f.deleteRecursively()
            } else if (f.isFile) {
                f.delete()
            }
        }
    }
}

fun clearCache() {
    removeExpired(sharedDir)
    removeExpired(tempDir)
}

@WorkerThread
fun buildLogFile(): File {
    val nowMillis = System.currentTimeMillis()
    val settings = storeFlow.value
    val request = SupportBundleRequest(
        generatedAtMillis = nowMillis,
        metadata = SupportBundleMetadata(
            appVersionName = META.versionName,
            appVersionCode = META.versionCode,
            flavor = META.channel,
            androidApi = Build.VERSION.SDK_INT,
        ),
        appSummary = SupportAppSummary(
            installSourceCategory = installSourceCategory(),
            appVersionName = META.versionName,
            appVersionCode = META.versionCode,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            androidApi = Build.VERSION.SDK_INT,
            featureFlags = linkedMapOf(
                "accessibilityGuard" to settings.accessibilityGuardEnabled,
                "appBlocker" to settings.enableBlockA11yAppList,
                "automation" to settings.enableAutomator,
                "selectorMatching" to settings.enableMatch,
                "shizuku" to settings.enableShizuku,
                "usageGuard" to settings.usageGuardEnabled,
            ),
        ),
        capabilitySummary = SupportCapabilitySummary(
            capabilities = buildMap {
                allPermissionStates.forEachIndexed { index, permission ->
                    put("permission_${index + 1}", permission.stateFlow.value)
                }
                put("shizukuConnected", shizukuContextFlow.value.ok)
            },
        ),
        diagnosticEvents = DiagnosticLogger.recentEvents().map { record ->
            SupportDiagnosticEvent(
                occurredAtMillis = record.occurredAtMinute,
                event = record.event,
            )
        },
        crashSummaries = recentCrashSummaries(),
    )
    val outputFile = sharedDir.resolve("support-$nowMillis.zip")
    return SupportBundleBuilder().build(outputFile, request)
}

private fun recentCrashSummaries(): List<SupportCrashSummary> = crashFolder.listFiles()
    .orEmpty()
    .asSequence()
    .filter(File::isFile)
    .mapNotNull { file ->
        runCatching { json.decodeFromString<CrashData>(file.readText()) }.getOrNull()
    }
    .sortedByDescending(CrashData::occurredAtMinute)
    .take(100)
    .map { crash ->
        SupportCrashSummary(
            errorCode = crash.errorCode,
            errorCategory = crash.errorCategory,
            occurredAtMillis = crash.occurredAtMinute,
            appFrames = crash.appFrames,
            count = crash.count,
        )
    }
    .toList()

private fun installSourceCategory(): String {
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            app.packageManager.getInstallSourceInfo(META.appId).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getInstallerPackageName(META.appId)
        }
    }.getOrNull()
    return when {
        installer == null -> "local-or-restored"
        installer.contains("vending", ignoreCase = true) -> "app-store"
        installer.contains("packageinstaller", ignoreCase = true) -> "package-installer"
        installer.contains("shell", ignoreCase = true) -> "developer-tool"
        else -> "other-store"
    }
}
