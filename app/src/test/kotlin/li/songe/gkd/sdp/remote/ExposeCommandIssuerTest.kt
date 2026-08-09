package li.songe.gkd.sdp.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposeCommandIssuerTest {
    @Test
    fun `external token is 256 bit single use action bound and persists only its hash`() = runBlocking {
        val store = InMemoryExposeCommandStore()
        val issuer = issuer(store) { 1_000 }

        val issued = issuer.issue(ExposeAction.SYNC_FIX, ExposeChannel.EXTERNAL)

        assertTrue(issued.token.length >= 43)
        assertEquals(301_000, issued.expiresAtMillis)
        assertFalse(store.records.joinToString().contains(issued.token))
        assertEquals(
            ExposeConsumeError.ACTION_MISMATCH,
            (issuer.consume(issued.token, ExposeAction.CAPTURE) as ExposeConsumeResult.Denied).error,
        )
        assertTrue(issuer.consume(issued.token, ExposeAction.SYNC_FIX) is ExposeConsumeResult.Allowed)
        assertEquals(
            ExposeConsumeError.ALREADY_CONSUMED,
            (issuer.consume(issued.token, ExposeAction.SYNC_FIX) as ExposeConsumeResult.Denied).error,
        )
    }

    @Test
    fun `new external token revokes old and internal token expires after thirty seconds`() = runBlocking {
        var now = 2_000L
        val issuer = issuer(InMemoryExposeCommandStore()) { now }
        val old = issuer.issue(ExposeAction.SYNC_FIX, ExposeChannel.EXTERNAL)
        val current = issuer.issue(ExposeAction.SYNC_FIX, ExposeChannel.EXTERNAL)
        assertEquals(
            ExposeConsumeError.TOKEN_MISMATCH,
            (issuer.consume(old.token, ExposeAction.SYNC_FIX) as ExposeConsumeResult.Denied).error,
        )
        assertTrue(issuer.consume(current.token, ExposeAction.SYNC_FIX) is ExposeConsumeResult.Allowed)

        val internal = issuer.issue(ExposeAction.STATUS_AUTOSTART, ExposeChannel.INTERNAL)
        now += 30_001
        assertEquals(
            ExposeConsumeError.EXPIRED,
            (issuer.consume(
                internal.token,
                ExposeAction.STATUS_AUTOSTART,
            ) as ExposeConsumeResult.Denied).error,
        )
    }

    private fun issuer(store: ExposeCommandStore, now: () -> Long) = ExposeCommandIssuer(
        store = store,
        nowMillis = now,
        randomSource = object : RemoteRandomSource {
            private var seed = 11
            override fun nextBytes(size: Int): ByteArray =
                ByteArray(size) { (seed++ and 0xff).toByte() }
        },
    )
}
