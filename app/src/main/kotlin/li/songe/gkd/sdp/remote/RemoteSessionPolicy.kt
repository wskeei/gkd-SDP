package li.songe.gkd.sdp.remote

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class RemoteSessionPolicy(
    private val randomSource: RemoteRandomSource = SecureRandom().let { random ->
        RemoteRandomSource { size -> ByteArray(size).also(random::nextBytes) }
    },
) {
    companion object {
        const val PAIRING_TTL_MILLIS = 60_000L
        const val SESSION_TTL_MILLIS = 15L * 60L * 1_000L
        const val MAX_PAIRING_FAILURES = 5
        const val TOKEN_BYTES = 32

        val defaultScopes = setOf(RemoteScope.SERVER_INFO, RemoteScope.SNAPSHOT_LIST)
    }

    private var mode = RemoteListenMode.LOCAL_ONLY
    private var pairingCode: String? = null
    private var pairingExpiresAtMillis: Long? = null
    private var accessExpiresAtMillis: Long? = null
    private var pairingFailures = 0
    private var tokenHash: ByteArray? = null
    private var clientIp: String? = null
    private var userAgentHash: String? = null
    private var clientSummary: String? = null
    private var origin: String? = null
    private var sessionExpiresAtMillis: Long? = null
    private var enabledScopes = defaultScopes

    @Synchronized
    fun start(newMode: RemoteListenMode, nowMillis: Long): RemoteSessionSnapshot {
        revokeLocked()
        mode = newMode
        pairingCode = newPairingCode()
        pairingExpiresAtMillis = nowMillis + PAIRING_TTL_MILLIS
        accessExpiresAtMillis = if (newMode == RemoteListenMode.LAN) {
            nowMillis + SESSION_TTL_MILLIS
        } else {
            null
        }
        pairingFailures = 0
        enabledScopes = defaultScopes
        return snapshotLocked()
    }

    @Synchronized
    fun pair(
        code: String,
        clientIp: String,
        userAgent: String,
        origin: String,
        nowMillis: Long,
    ): RemotePairResult {
        val expectedCode = pairingCode
            ?: return RemotePairResult.Failure(RemotePairError.NO_CHALLENGE, 0)
        val pairingExpiry = pairingExpiresAtMillis
            ?: return RemotePairResult.Failure(RemotePairError.NO_CHALLENGE, 0)
        val accessExpiry = accessExpiresAtMillis
        if (nowMillis >= pairingExpiry || accessExpiry != null && nowMillis >= accessExpiry) {
            pairingCode = null
            return RemotePairResult.Failure(RemotePairError.EXPIRED, 0)
        }
        val normalizedOrigin = normalizeOrigin(origin)
            ?: return RemotePairResult.Failure(
                RemotePairError.INVALID_ORIGIN,
                MAX_PAIRING_FAILURES - pairingFailures,
            )
        if (clientIp.isBlank() || userAgent.isBlank()) {
            return RemotePairResult.Failure(
                RemotePairError.INVALID_CLIENT,
                MAX_PAIRING_FAILURES - pairingFailures,
            )
        }
        if (code != expectedCode) {
            pairingFailures++
            if (pairingFailures >= MAX_PAIRING_FAILURES) pairingCode = null
            return RemotePairResult.Failure(
                if (pairingFailures >= MAX_PAIRING_FAILURES) {
                    RemotePairError.TOO_MANY_ATTEMPTS
                } else {
                    RemotePairError.INVALID_CODE
                },
                (MAX_PAIRING_FAILURES - pairingFailures).coerceAtLeast(0),
            )
        }

        val tokenBytes = randomSource.nextBytes(TOKEN_BYTES)
        require(tokenBytes.size == TOKEN_BYTES)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        tokenHash = sha256(token.encodeToByteArray())
        this.clientIp = clientIp
        userAgentHash = shortHash(userAgent)
        clientSummary = "client-${shortHash("$clientIp\u0000$userAgent")}"
        this.origin = normalizedOrigin
        sessionExpiresAtMillis = minOf(
            nowMillis + SESSION_TTL_MILLIS,
            accessExpiry ?: Long.MAX_VALUE,
        )
        pairingCode = null
        pairingExpiresAtMillis = null
        return RemotePairResult.Success(token, requireNotNull(sessionExpiresAtMillis))
    }

    @Synchronized
    fun authorize(
        token: String,
        clientIp: String,
        userAgent: String,
        origin: String?,
        requiredScope: RemoteScope,
        nowMillis: Long,
    ): RemoteAuthorizationResult {
        val expectedHash = tokenHash
            ?: return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.NO_SESSION)
        val expiry = sessionExpiresAtMillis
            ?: return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.NO_SESSION)
        if (nowMillis >= expiry) {
            revokeLocked()
            return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.EXPIRED)
        }
        if (!MessageDigest.isEqual(expectedHash, sha256(token.encodeToByteArray()))) {
            return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.TOKEN_MISMATCH)
        }
        if (clientIp != this.clientIp || shortHash(userAgent) != userAgentHash) {
            return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.CLIENT_MISMATCH)
        }
        if (normalizeOrigin(origin) != this.origin) {
            return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.ORIGIN_MISMATCH)
        }
        if (requiredScope !in enabledScopes) {
            return RemoteAuthorizationResult.Denied(RemoteAuthorizationError.SCOPE_DENIED)
        }
        return RemoteAuthorizationResult.Allowed
    }

    @Synchronized
    fun setScope(scope: RemoteScope, enabled: Boolean): RemoteSessionSnapshot {
        enabledScopes = if (enabled) enabledScopes + scope else enabledScopes - scope
        return snapshotLocked()
    }

    @Synchronized
    fun revoke(reason: RemoteRevocationReason): RemoteSessionSnapshot {
        @Suppress("UNUSED_VARIABLE")
        val stableReason = reason
        revokeLocked()
        return snapshotLocked()
    }

    @Synchronized
    fun snapshot(): RemoteSessionSnapshot = snapshotLocked()

    private fun snapshotLocked() = RemoteSessionSnapshot(
        mode = mode,
        host = mode.host,
        pairingCode = pairingCode,
        pairingExpiresAtMillis = pairingExpiresAtMillis,
        accessExpiresAtMillis = accessExpiresAtMillis,
        sessionExpiresAtMillis = sessionExpiresAtMillis,
        paired = tokenHash != null,
        clientSummary = clientSummary,
        origin = origin,
        enabledScopes = enabledScopes,
    )

    private fun revokeLocked() {
        tokenHash?.fill(0)
        tokenHash = null
        clientIp = null
        userAgentHash = null
        clientSummary = null
        origin = null
        sessionExpiresAtMillis = null
        pairingCode = null
        pairingExpiresAtMillis = null
        pairingFailures = 0
    }

    private fun newPairingCode(): String {
        val bytes = randomSource.nextBytes(Int.SIZE_BYTES)
        require(bytes.size == Int.SIZE_BYTES)
        val number = bytes.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xff) }
        return (number % 100_000_000L).toString().padStart(8, '0')
    }

    private fun normalizeOrigin(value: String?): String? {
        if (value.isNullOrBlank() || value == "null" || value == "*") return null
        return runCatching {
            val uri = URI(value)
            if (
                uri.scheme !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                uri.rawPath.orEmpty().let { it.isNotEmpty() && it != "/" } ||
                uri.rawQuery != null ||
                uri.rawFragment != null
            ) return null
            val scheme = uri.scheme.lowercase()
            val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
            "$scheme://${uri.host.lowercase()}:$port"
        }.getOrNull()
    }

    private fun shortHash(value: String): String = sha256(value.encodeToByteArray())
        .take(6)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)
}
