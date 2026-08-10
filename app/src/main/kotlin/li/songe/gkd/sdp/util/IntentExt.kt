package li.songe.gkd.sdp.util

import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.isActivityVisible
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.permission.canWriteExternalStorage
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.service.AccessibilityGuardRuntime
import java.io.File
import kotlin.reflect.KClass
import li.songe.gkd.sdp.R

fun MainActivity.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(
        app, "${app.packageName}.provider", file
    )
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    tryStartActivity(
        Intent.createChooser(
            intent, title
        )
    )
}

suspend fun MainActivity.saveFileToDownloads(file: File) {
    if (AndroidTarget.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        withContext(Dispatchers.IO) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("创建URI失败")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(file.readBytes())
                outputStream.flush()
            }
        }
    } else {
        requiredPermission(this, canWriteExternalStorage)
        val targetFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            file.name
        )
        targetFile.writeBytes(file.readBytes())
    }
    toast(app.getString(R.string.s_9376a4238e, file.name))
}

fun Context.tryStartActivity(intent: Intent): Boolean {
    try {
        startActivity(intent)
        return true
    } catch (e: Exception) {
        LogUtils.d("tryStartActivity", e)
        toast(app.getString(R.string.s_475787d680, DiagnosticLogger.userMessage(e)))
        return false
    }
}

fun openWeChatScaner() {
    val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
        putExtra("LauncherUI.From.Scaner.Shortcut", true)
    }
    if (intent == null) {
        toast(app.getString(R.string.s_60d171dc16))
        return
    }
    app.tryStartActivity(intent)
}

fun openA11ySettings() {
    AccessibilityGuardRuntime.beginGrantFlow()
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (!app.tryStartActivity(intent)) {
        AccessibilityGuardRuntime.cancelGrantFlow()
    }
}

fun openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${app.packageName}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        uri.toUri()
    } catch (e: Exception) {
        LogUtils.d("invalid URI", e)
        toast(app.getString(R.string.s_e7e0ffcd50))
        return
    }
    openUri(u)
}

fun openUri(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

fun openApp(appId: String) {
    val intent = app.packageManager.getLaunchIntentForPackage(appId)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.tryStartActivity(intent)
    } else {
        toast(app.getString(R.string.s_a7e6272535))
    }
}

fun <T : Service> stopServiceByClass(clazz: KClass<T>) {
    val intent = Intent(app, clazz.java)
    app.stopService(intent)
}

fun <T : Service> startForegroundServiceByClass(clazz: KClass<T>): Boolean {
    if (!notificationState.checkOrToast()) return false
    if (!foregroundServiceSpecialUseState.checkOrToast()) return false
    val intent = Intent(app, clazz.java)
    try {
        app.startForegroundService(intent)
        return true
    } catch (e: Throwable) {
        LogUtils.d(e)
        val prefix = if (isActivityVisible) "" else "${META.appName}: "
        toast(app.getString(R.string.s_73e7e97c6d, prefix, DiagnosticLogger.userMessage(e)), forced = true)
        return false
    }
}

val Intent.extraCptName: ComponentName?
    get() = if (AndroidTarget.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName?
    }
