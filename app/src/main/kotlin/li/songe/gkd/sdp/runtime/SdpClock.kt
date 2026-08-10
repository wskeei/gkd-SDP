package li.songe.gkd.sdp.runtime

import android.os.SystemClock

/**
 * Time sources used by self-control policies.
 *
 * Wall-clock time is persisted and shown to users. Monotonic time is only used
 * for elapsed durations and deadlines so a user changing the device clock
 * cannot create a negative usage duration or bypass a timeout.
 */
interface SdpClock {
    fun nowEpochMillis(): Long

    fun elapsedRealtimeMillis(): Long
}

object SystemSdpClock : SdpClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
