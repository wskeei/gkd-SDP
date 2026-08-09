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
}
