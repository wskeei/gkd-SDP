package li.songe.gkd.sdp.remote

enum class RemoteListenMode(val host: String) {
    LOCAL_ONLY("127.0.0.1"),
    LAN("0.0.0.0"),
}

enum class RemoteScope {
    SERVER_INFO,
    SNAPSHOT_LIST,
    VIEW_SNAPSHOT,
    CAPTURE_SNAPSHOT,
    DELETE_SNAPSHOT,
    UPDATE_SUBSCRIPTION,
    EXEC_SELECTOR,
}

data class RemoteSessionSnapshot(
    val mode: RemoteListenMode,
    val host: String,
    val pairingCode: String?,
    val pairingExpiresAtMillis: Long?,
    val accessExpiresAtMillis: Long?,
    val sessionExpiresAtMillis: Long?,
    val paired: Boolean,
    val clientSummary: String?,
    val origin: String?,
    val enabledScopes: Set<RemoteScope>,
)

sealed interface RemotePairResult {
    data class Success(
        val token: String,
        val expiresAtMillis: Long,
    ) : RemotePairResult

    data class Failure(
        val error: RemotePairError,
        val attemptsRemaining: Int,
    ) : RemotePairResult
}

enum class RemotePairError {
    NO_CHALLENGE,
    EXPIRED,
    INVALID_CODE,
    TOO_MANY_ATTEMPTS,
    INVALID_CLIENT,
    INVALID_ORIGIN,
}

sealed interface RemoteAuthorizationResult {
    data object Allowed : RemoteAuthorizationResult
    data class Denied(val error: RemoteAuthorizationError) : RemoteAuthorizationResult
}

enum class RemoteAuthorizationError {
    NO_SESSION,
    EXPIRED,
    TOKEN_MISMATCH,
    CLIENT_MISMATCH,
    ORIGIN_MISMATCH,
    SCOPE_DENIED,
}

enum class RemoteRevocationReason {
    USER_DISCONNECTED,
    SERVICE_STOPPED,
    LOCAL_ONLY_SELECTED,
    DEVICE_LOCKED,
    EXPIRED,
    REPLACED,
}

fun interface RemoteRandomSource {
    fun nextBytes(size: Int): ByteArray
}
