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

    @Test
    fun pairingRejectsMissingExpiredAndMalformedChallenges() {
        val policy = policy()

        val noChallenge = policy.pair("12345678", "ip", "ua", "http://host:8888", 1_000)
        assertEquals(RemotePairError.NO_CHALLENGE, (noChallenge as RemotePairResult.Failure).error)

        val started = policy.start(RemoteListenMode.LOCAL_ONLY, 1_000)
        val expired = policy.pair(
            requireNotNull(started.pairingCode),
            "ip",
            "ua",
            "http://host:8888",
            1_000 + RemoteSessionPolicy.PAIRING_TTL_MILLIS,
        )
        assertEquals(RemotePairError.EXPIRED, (expired as RemotePairResult.Failure).error)

        policy.start(RemoteListenMode.LOCAL_ONLY, 2_000)
        val invalidOrigin = policy.pair("12345678", "ip", "ua", "javascript:alert(1)", 2_001)
        assertEquals(RemotePairError.INVALID_ORIGIN, (invalidOrigin as RemotePairResult.Failure).error)

        val invalidClient = policy.pair(
            requireNotNull(policy.start(RemoteListenMode.LOCAL_ONLY, 3_000).pairingCode),
            "",
            "ua",
            "http://host:8888",
            3_001,
        )
        assertEquals(RemotePairError.INVALID_CLIENT, (invalidClient as RemotePairResult.Failure).error)
    }

    @Test
    fun pairingInvalidCodeTracksRemainingAttempts() {
        val policy = policy()
        val started = policy.start(RemoteListenMode.LOCAL_ONLY, 1_000)

        val first = policy.pair("00000000", "ip", "ua", "http://host:8888", 1_001)

        assertEquals(RemotePairError.INVALID_CODE, (first as RemotePairResult.Failure).error)
        assertEquals(4, first.attemptsRemaining)
    }

    @Test
    fun authorizationRejectsExpiredTokensAndWrongToken() {
        val policy = policy()
        val started = policy.start(RemoteListenMode.LAN, 1_000)
        val token = (policy.pair(
            requireNotNull(started.pairingCode),
            "ip",
            "ua",
            "http://host:8888",
            1_001,
        ) as RemotePairResult.Success).token

        val expired = policy.authorize(
            token,
            "ip",
            "ua",
            "http://host:8888",
            RemoteScope.SERVER_INFO,
            1_000 + RemoteSessionPolicy.SESSION_TTL_MILLIS,
        )
        assertEquals(RemoteAuthorizationError.EXPIRED, (expired as RemoteAuthorizationResult.Denied).error)

        val restarted = policy.start(RemoteListenMode.LAN, 2_000)
        val newToken = (policy.pair(
            requireNotNull(restarted.pairingCode),
            "ip",
            "ua",
            "http://host:8888",
            2_001,
        ) as RemotePairResult.Success).token
        val wrong = policy.authorize(
            "wrong-token",
            "ip",
            "ua",
            "http://host:8888",
            RemoteScope.SERVER_INFO,
            2_002,
        )
        assertEquals(RemoteAuthorizationError.TOKEN_MISMATCH, (wrong as RemoteAuthorizationResult.Denied).error)
        assertTrue(policy.authorize(
            newToken,
            "ip",
            "ua",
            "http://host:8888",
            RemoteScope.SERVER_INFO,
            2_002,
        ) is RemoteAuthorizationResult.Allowed)
    }

    @Test
    fun scopeChangesAndSnapshotsRemainConsistent() {
        val policy = policy()
        val started = policy.start(RemoteListenMode.LOCAL_ONLY, 1_000)
        policy.pair(
            requireNotNull(started.pairingCode),
            "ip",
            "ua",
            "http://host:8888",
            1_001,
        )

        policy.setScope(RemoteScope.EXEC_SELECTOR, enabled = true)
        assertTrue(policy.snapshot().enabledScopes.contains(RemoteScope.EXEC_SELECTOR))

        policy.setScope(RemoteScope.SERVER_INFO, enabled = false)
        assertFalse(policy.snapshot().enabledScopes.contains(RemoteScope.SERVER_INFO))

        policy.revoke(RemoteRevocationReason.USER_DISCONNECTED)
        assertEquals(RemoteListenMode.LOCAL_ONLY, policy.snapshot().mode)
        assertFalse(policy.snapshot().paired)
    }

    private fun policy() = RemoteSessionPolicy(
        randomSource = object : RemoteRandomSource {
            private var seed = 1
            override fun nextBytes(size: Int): ByteArray =
                ByteArray(size) { (seed++ and 0xff).toByte() }
        },
    )
}
