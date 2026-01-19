package li.songe.gkd.sdp.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app

sealed class NotifChannel(
    val id: String,
    val name: String? = null,
    val desc: String? = null,
) {
    data object Default : NotifChannel(
        id = "0",
    )

    data object Snapshot : NotifChannel(
        id = "1",
        name = "保存快照通知",
    )

    data object FocusMode : NotifChannel(
        id = "2",
        name = "专注模式通知",
        desc = "专注会话开始和结束通知",
    )
}

fun initChannel() {
    val channels = arrayOf(NotifChannel.Default, NotifChannel.Snapshot, NotifChannel.FocusMode)
    val manager = NotificationManagerCompat.from(app)
    // delete old channels
    manager.notificationChannels.filter { channels.none { c -> c.id == it.id } }.forEach {
        manager.deleteNotificationChannel(it.id)
    }
    // create/update new channels
    channels.forEach {
        val channel = NotificationChannel(
            it.id,
            it.name ?: META.appName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = it.desc
        }
        manager.createNotificationChannel(channel)
    }
}
