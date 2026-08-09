package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSessionPolicyTest {
    @Test
    fun `service is loopback by default and lan access requires an explicit window`() {
        val policy = policy()

        val local = policy.start(RemoteListenMode.LOCAL_ONLY, nowMillis = 1_000)
        assertEquals("127.0.0.1", local.host)
        assertEquals(8, local.pairingCode?.length)
        assertEquals(61_000L, local.pairingExpiresAtMillis)

        val lan = policy.start(RemoteListenMode.LAN, nowMillis = 2_000)
        assertEquals("0.0.0.0", lan.host)
        assertEquals(902_000L, lan.accessExpiresAtMillis)
        assertNotEquals(local.pairingCode, lan.pairingCode)
    }

    @Test
    fun `pairing binds a 256 bit token to one client origin and default scopes`() {
        val policy = policy()
        val started = policy.start(RemoteListenMode.LAN, nowMillis = 1_000)

        val paired = policy.pair(
            code = requireNotNull(started.pairingCode),
            clientIp = "192.168.1.10",
            userAgent = "Synthetic Browser",
            origin = "http://192.168.1.2:8888",
            nowMillis = 1_500,
        ) as RemotePairResult.Success

        assertTrue(paired.token.length >= 43)
        assertEquals(901_000, paired.expiresAtMillis)
        assertEquals(
            setOf(RemoteScope.SERVER_INFO, RemoteScope.SNAPSHOT_LIST),
            policy.snapshot().enabledScopes,
        )
        assertTrue(
            policy.authorize(
                token = paired.token,
                clientIp = "192.168.1.10",
                userAgent = "Synthetic Browser",
                origin = "http://192.168.1.2:8888",
                requiredScope = RemoteScope.SERVER_INFO,
                nowMillis = 2_000,
            ) is RemoteAuthorizationResult.Allowed,
        )
        assertEquals(
            RemoteAuthorizationError.CLIENT_MISMATCH,
            (policy.authorize(
                token = paired.token,
                clientIp = "192.168.1.11",
                userAgent = "Synthetic Browser",
                origin = "http://192.168.1.2:8888",
                requiredScope = RemoteScope.SERVER_INFO,
                nowMillis = 2_000,
            ) as RemoteAuthorizationResult.Denied).error,
        )
        assertEquals(
            RemoteAuthorizationError.ORIGIN_MISMATCH,
            (policy.authorize(
                token = paired.token,
                clientIp = "192.168.1.10",
                userAgent = "Synthetic Browser",
                origin = "null",
                requiredScope = RemoteScope.SERVER_INFO,
                nowMillis = 2_000,
            ) as RemoteAuthorizationResult.Denied).error,
        )
        assertEquals(
            RemoteAuthorizationError.SCOPE_DENIED,
            (policy.authorize(
                token = paired.token,
                clientIp = "192.168.1.10",
                userAgent = "Synthetic Browser",
                origin = "http://192.168.1.2:8888",
                requiredScope = RemoteScope.EXEC_SELECTOR,
                nowMillis = 2_000,
            ) as RemoteAuthorizationResult.Denied).error,
        )
    }

    @Test
    fun `pairing permits five failures and all revocation paths invalidate the token`() {
        val policy = policy()
        val started = policy.start(RemoteListenMode.LAN, 10_000)
        repeat(4) {
            val result = policy.pair("00000000", "ip", "ua", "http://host:8888", 10_001L + it)
            assertTrue(result is RemotePairResult.Failure)
        }
        val fifth = policy.pair("00000000", "ip", "ua", "http://host:8888", 10_010)
        assertEquals(RemotePairError.TOO_MANY_ATTEMPTS, (fifth as RemotePairResult.Failure).error)

        val restarted = policy.start(RemoteListenMode.LAN, 20_000)
        val token = (policy.pair(
            requireNotNull(restarted.pairingCode),
            "ip",
            "ua",
            "http://host:8888",
            20_001,
        ) as RemotePairResult.Success).token
        policy.revoke(RemoteRevocationReason.DEVICE_LOCKED)
        assertFalse(policy.snapshot().paired)
        assertEquals(
            RemoteAuthorizationError.NO_SESSION,
            (policy.authorize(
                token,
                "ip",
                "ua",
                "http://host:8888",
                RemoteScope.SERVER_INFO,
                20_002,
            ) as RemoteAuthorizationResult.Denied).error,
        )
    }

    private fun policy() = RemoteSessionPolicy(
        randomSource = object : RemoteRandomSource {
            private var seed = 1
            override fun nextBytes(size: Int): ByteArray =
                ByteArray(size) { (seed++ and 0xff).toByte() }
        },
    )
}
