package li.songe.gkd.sdp.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

sealed class NotifChannel(
    val id: String,
    val name: String? = null,
    val desc: String? = null,
    val importance: Int = NotificationManager.IMPORTANCE_LOW,
) {
    data object Default : NotifChannel(
        id = "0",
    )

    data object Snapshot : NotifChannel(
        id = "1",
        name = app.getString(R.string.notif_channel_snapshot),
    )

    data object FocusMode : NotifChannel(
        id = "2",
        name = app.getString(R.string.notif_channel_focus),
        desc = app.getString(R.string.notif_channel_focus_desc),
    )

    data object AccessibilityGuard : NotifChannel(
        id = "3",
        name = app.getString(R.string.notif_channel_guard),
        desc = app.getString(R.string.notif_channel_guard_desc),
        importance = NotificationManager.IMPORTANCE_HIGH,
    )
}

fun initChannel() {
    val channels = arrayOf(
        NotifChannel.Default,
        NotifChannel.Snapshot,
        NotifChannel.FocusMode,
        NotifChannel.AccessibilityGuard,
    )
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
            it.importance,
        ).apply {
            description = it.desc
        }
        manager.createNotificationChannel(channel)
    }
}
