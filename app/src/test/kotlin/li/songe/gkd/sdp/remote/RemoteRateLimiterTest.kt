package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRateLimiterTest {
    @Test
    fun `enforces total capture and selector limits in a rolling minute`() {
        val limiter = RemoteRateLimiter()
        repeat(6) { assertTrue(limiter.check("session", RemoteRequestKind.CAPTURE, it.toLong()).allowed) }
        assertEquals(60, limiter.check("session", RemoteRequestKind.CAPTURE, 10).retryAfterSeconds)

        val execLimiter = RemoteRateLimiter()
        repeat(10) { assertTrue(execLimiter.check("session", RemoteRequestKind.EXEC, it.toLong()).allowed) }
        assertEquals(60, execLimiter.check("session", RemoteRequestKind.EXEC, 10).retryAfterSeconds)

        val totalLimiter = RemoteRateLimiter()
        repeat(60) { assertTrue(totalLimiter.check("session", RemoteRequestKind.DEFAULT, it.toLong()).allowed) }
        assertEquals(60, totalLimiter.check("session", RemoteRequestKind.DEFAULT, 60).retryAfterSeconds)
        assertTrue(totalLimiter.check("session", RemoteRequestKind.DEFAULT, 60_001).allowed)
    }

    @Test
    fun `rejects request and response resource limits`() {
        val limiter = RemoteRateLimiter()
        assertEquals(
            RemoteLimitError.REQUEST_TOO_LARGE,
            limiter.validateRequestBytes(RemoteRateLimiter.MAX_REQUEST_BYTES + 1).error,
        )
        assertEquals(
            RemoteLimitError.RESPONSE_TOO_LARGE,
            limiter.validateResponseBytes(RemoteRateLimiter.MAX_RESPONSE_BYTES + 1).error,
        )
    }

    @Test
    fun `exact resource limits and zero bytes are allowed`() {
        val limiter = RemoteRateLimiter()
        assertTrue(limiter.validateRequestBytes(0L).allowed)
        assertTrue(limiter.validateRequestBytes(RemoteRateLimiter.MAX_REQUEST_BYTES).allowed)
        assertTrue(limiter.validateResponseBytes(0L).allowed)
        assertTrue(limiter.validateResponseBytes(RemoteRateLimiter.MAX_RESPONSE_BYTES).allowed)
    }

    @Test
    fun `clear removes only the requested session`() {
        val limiter = RemoteRateLimiter()
        limiter.check("a", RemoteRequestKind.DEFAULT, 0)
        repeat(60) { limiter.check("b", RemoteRequestKind.DEFAULT, it.toLong()) }

        limiter.clear("a")

        assertTrue(limiter.check("a", RemoteRequestKind.DEFAULT, 1).allowed)
        assertEquals(
            RemoteLimitError.RATE_LIMITED,
            limiter.check("b", RemoteRequestKind.DEFAULT, 1).error,
        )
    }

    @Test
    fun `rolling prune releases total and specialized queues`() {
        val limiter = RemoteRateLimiter()
        repeat(6) { limiter.check("session", RemoteRequestKind.CAPTURE, it.toLong()) }
        assertEquals(60, limiter.check("session", RemoteRequestKind.CAPTURE, 10).retryAfterSeconds)
        assertTrue(limiter.check("session", RemoteRequestKind.CAPTURE, 60_001).allowed)

        limiter.clear()
        repeat(60) { limiter.check("session", RemoteRequestKind.DEFAULT, 61_000L + it) }
        assertEquals(60, limiter.check("session", RemoteRequestKind.DEFAULT, 61_060).retryAfterSeconds)
        assertTrue(limiter.check("session", RemoteRequestKind.DEFAULT, 121_060L).allowed)
    }
}
