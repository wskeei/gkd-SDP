package li.songe.gkd.sdp.runtime

import java.util.concurrent.atomic.AtomicLong

class FakeSdpClock(
    epochMillis: Long = 0L,
    elapsedMillis: Long = 0L,
) : SdpClock {
    private val epoch = AtomicLong(epochMillis)
    private val elapsed = AtomicLong(elapsedMillis)

    override fun nowEpochMillis(): Long = epoch.get()

    override fun elapsedRealtimeMillis(): Long = elapsed.get()

    fun setEpochMillis(value: Long) {
        epoch.set(value)
    }

    fun setElapsedRealtimeMillis(value: Long) {
        elapsed.set(value)
    }

    fun advanceEpochMillis(delta: Long) {
        epoch.addAndGet(delta)
    }

    fun advanceElapsedRealtimeMillis(delta: Long) {
        elapsed.addAndGet(delta)
    }
}
