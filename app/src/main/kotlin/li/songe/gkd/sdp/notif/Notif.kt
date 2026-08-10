package li.songe.gkd.sdp.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import kotlinx.atomicfu.atomic
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.ButtonService
import li.songe.gkd.sdp.service.EventService
import li.songe.gkd.sdp.service.HttpService
import li.songe.gkd.sdp.service.ScreenshotService
import li.songe.gkd.sdp.service.TrackService
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.AccessibilityGuardNotificationPolicy
import li.songe.gkd.sdp.util.componentName
import kotlin.reflect.KClass

// 相同的 request code 会导致后续 PendingIntent 失效
private val pendingIntentReqId = atomic(0)

data class Notif(
    val channel: NotifChannel = NotifChannel.Default,
    val id: Int,
    val smallIcon: Int = R.drawable.ic_status,
    val title: String,
    val text: String? = null,
    val ongoing: Boolean = true,
    val autoCancel: Boolean = false,
    val priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    val category: String? = null,
    val uri: String? = null,
    val whenEpochMs: Long? = null,
    val usesChronometer: Boolean = false,
    val chronometerCountDown: Boolean = false,
    val onlyAlertOnce: Boolean = false,
    val stopService: KClass<out Service>? = null,
) {
    private fun toNotification(): Notification {
        val contextIntent = PendingIntent.getActivity(
            app,
            pendingIntentReqId.incrementAndGet(),
            Intent().apply {
                component = MainActivity::class.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data = uri?.toUri()
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel.id)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contextIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .setPriority(priority)
            .setOnlyAlertOnce(onlyAlertOnce)
            .apply {
                if (category != null) setCategory(category)
                if (whenEpochMs != null) {
                    setWhen(whenEpochMs)
                }
                if (usesChronometer) {
                    setUsesChronometer(true)
                    setChronometerCountDown(chronometerCountDown)
                }
            }
        if (stopService != null) {
            val deleteIntent = PendingIntent.getBroadcast(
                app,
                pendingIntentReqId.incrementAndGet(),
                StopServiceReceiver.getIntent(stopService),
                PendingIntent.FLAG_IMMUTABLE
            )
            notification
                .setDeleteIntent(deleteIntent)
                .addAction(0, "停止", deleteIntent)
        }
        return notification.build()
    }

    fun notifySelf() {
        if (!notificationState.updateAndGet()) return
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(app).notify(id, toNotification())
    }

    context(service: Service)
    fun notifyService() {
        if (!notificationState.updateAndGet()) return
        if (!foregroundServiceSpecialUseState.updateAndGet()) return
        ServiceCompat.startForeground(
            service,
            id,
            toNotification(),
            if (AndroidTarget.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST else -1
        )
    }
}

val abNotif by lazy {
    Notif(
        id = 100,
        title = META.appName,
        text = "无障碍正在运行",
    )
}

val screenshotNotif = Notif(
    id = 101,
    title = "截屏服务正在运行",
    text = "保存快照时截取屏幕",
    uri = "gkd://settings/privacy-data",
    stopService = ScreenshotService::class,
)

val buttonNotif = Notif(
    id = 102,
    title = "快照按钮服务正在运行",
    text = "点击按钮捕获快照",
    uri = "gkd://settings/privacy-data",
    stopService = ButtonService::class,
)

val httpNotif = Notif(
    id = 103,
    title = "HTTP服务正在运行",
    uri = "gkd://settings/privacy-data",
    stopService = HttpService::class,
)

val exposeNotif = Notif(
    id = 104,
    title = "运行外部调用任务中",
    text = "任务完成后自动关闭",
)

val snapshotNotif = Notif(
    channel = NotifChannel.Snapshot,
    id = 105,
    title = "快照已保存",
    ongoing = false,
    autoCancel = true,
    uri = "gkd://snapshots",
)

val recordNotif = Notif(
    id = 106,
    title = "记录服务正在运行",
    uri = "gkd://settings/privacy-data",
    stopService = ActivityService::class,
)

val eventNotif = Notif(
    id = 107,
    title = "事件服务正在运行",
    uri = "gkd://settings/privacy-data",
    stopService = EventService::class,
)

val trackNotif = Notif(
    id = 108,
    title = "轨迹服务正在运行",
    uri = "gkd://settings",
    stopService = TrackService::class,
)

val focusEndNotif = Notif(
    channel = NotifChannel.FocusMode,
    id = 109,
    title = "专注结束",
    text = "专注时间已结束，做得很好！",
    ongoing = false,
    autoCancel = true,
)

const val ACCESSIBILITY_GUARD_NOTIF_ID_START = 110
const val ACCESSIBILITY_GUARD_NOTIF_COUNT = 6
const val ACCESSIBILITY_GUARD_STATUS_NOTIF_ID = 116

fun accessibilityGuardNotif(index: Int): Notif {
    require(index in 0 until ACCESSIBILITY_GUARD_NOTIF_COUNT)
    return Notif(
        channel = NotifChannel.AccessibilityGuard,
        id = ACCESSIBILITY_GUARD_NOTIF_ID_START + index,
        title = AccessibilityGuardNotificationPolicy.TITLE,
        text = AccessibilityGuardNotificationPolicy.text(index),
        ongoing = false,
        autoCancel = true,
        uri = "gkd://overview",
        priority = NotificationCompat.PRIORITY_HIGH,
        category = NotificationCompat.CATEGORY_ERROR,
    )
}

private fun accessibilityGuardStatusNotif(
    status: AccessibilityGuardNotificationPolicy.GuardStatusNotification,
): Notif {
    val hasCountdown = status.targetEpochMs != null
    return Notif(
        channel = NotifChannel.AccessibilityGuard,
        id = ACCESSIBILITY_GUARD_STATUS_NOTIF_ID,
        title = AccessibilityGuardNotificationPolicy.TITLE,
        text = status.text,
        ongoing = true,
        autoCancel = false,
        priority = NotificationCompat.PRIORITY_HIGH,
        category = NotificationCompat.CATEGORY_ERROR,
        uri = "gkd://self-control",
        whenEpochMs = status.targetEpochMs,
        usesChronometer = hasCountdown,
        chronometerCountDown = hasCountdown,
        onlyAlertOnce = true,
    )
}

fun cancelAccessibilityGuardReminderNotifications() {
    val manager = NotificationManagerCompat.from(app)
    repeat(ACCESSIBILITY_GUARD_NOTIF_COUNT) { index ->
        manager.cancel(ACCESSIBILITY_GUARD_NOTIF_ID_START + index)
    }
}

fun cancelAccessibilityGuardStatusNotification() {
    NotificationManagerCompat.from(app).cancel(ACCESSIBILITY_GUARD_STATUS_NOTIF_ID)
}

fun cancelAccessibilityGuardNotifications() {
    cancelAccessibilityGuardReminderNotifications()
    cancelAccessibilityGuardStatusNotification()
}

fun postAccessibilityGuardNotification(index: Int) {
    cancelAccessibilityGuardReminderNotifications()
    accessibilityGuardNotif(index).notifySelf()
}

fun postAccessibilityGuardStatusNotification(
    status: AccessibilityGuardNotificationPolicy.GuardStatusNotification,
) {
    accessibilityGuardStatusNotif(status).notifySelf()
}
