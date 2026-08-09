package li.songe.gkd.sdp.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable
enum class ExposeAction {
    STATUS_AUTOSTART,
    CAPTURE,
    SYNC_FIX,
}

@Serializable
enum class ExposeChannel {
    EXTERNAL,
    INTERNAL,
}

@Serializable
data class ExposeCommandRecord(
    val tokenHash: String,
    val action: ExposeAction,
    val channel: ExposeChannel,
    val expiresAtMillis: Long,
    val consumedAtMillis: Long? = null,
)

data class IssuedExposeCommand(
    val token: String,
    val action: ExposeAction,
    val expiresAtMillis: Long,
)

interface ExposeCommandStore {
    suspend fun load(): List<ExposeCommandRecord>
    suspend fun save(records: List<ExposeCommandRecord>)
}

class InMemoryExposeCommandStore : ExposeCommandStore {
    var records: List<ExposeCommandRecord> = emptyList()
        private set

    override suspend fun load(): List<ExposeCommandRecord> = records

    override suspend fun save(records: List<ExposeCommandRecord>) {
        this.records = records
    }
}

class FileExposeCommandStore(
    private val file: File,
    private val codec: Json = Json { encodeDefaults = true },
) : ExposeCommandStore {
    override suspend fun load(): List<ExposeCommandRecord> = file.takeIf(File::isFile)?.let {
        runCatching { codec.decodeFromString<List<ExposeCommandRecord>>(it.readText()) }
            .getOrDefault(emptyList())
    }.orEmpty()

    override suspend fun save(records: List<ExposeCommandRecord>) {
        if (records.isEmpty()) {
            file.delete()
            file.parentFile?.resolve("${file.name}.tmp")?.delete()
        } else {
            writeTextAtomically(file, codec.encodeToString(records))
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }
}

sealed interface ExposeConsumeResult {
    data object Allowed : ExposeConsumeResult
    data class Denied(val error: ExposeConsumeError) : ExposeConsumeResult
}

enum class ExposeConsumeError {
    TOKEN_MISMATCH,
    ACTION_MISMATCH,
    CHANNEL_MISMATCH,
    EXPIRED,
    ALREADY_CONSUMED,
}

class ExposeCommandIssuer(
    private val store: ExposeCommandStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val randomSource: RemoteRandomSource = SecureRandom().let { random ->
        RemoteRandomSource { size -> ByteArray(size).also(random::nextBytes) }
    },
) {
    companion object {
        const val TOKEN_BYTES = 32
        const val EXTERNAL_TTL_MILLIS = 5L * 60L * 1_000L
        const val INTERNAL_TTL_MILLIS = 30L * 1_000L
    }

    private val mutex = Mutex()

    suspend fun issue(action: ExposeAction, channel: ExposeChannel): IssuedExposeCommand =
        mutex.withLock {
            val now = nowMillis()
            val tokenBytes = randomSource.nextBytes(TOKEN_BYTES)
            require(tokenBytes.size == TOKEN_BYTES)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
            tokenBytes.fill(0)
            val expiresAt = now + when (channel) {
                ExposeChannel.EXTERNAL -> EXTERNAL_TTL_MILLIS
                ExposeChannel.INTERNAL -> INTERNAL_TTL_MILLIS
            }
            val current = store.load().filter { record ->
                record.channel != channel && record.expiresAtMillis > now
            }
            store.save(
                current + ExposeCommandRecord(
                    tokenHash = tokenHash(token),
                    action = action,
                    channel = channel,
                    expiresAtMillis = expiresAt,
                ),
            )
            IssuedExposeCommand(token, action, expiresAt)
        }

    suspend fun consume(
        token: String,
        action: ExposeAction,
        channel: ExposeChannel? = null,
    ): ExposeConsumeResult = mutex.withLock {
        val now = nowMillis()
        val records = store.load()
        val digest = tokenHash(token)
        val index = records.indexOfFirst { record -> constantTimeEquals(record.tokenHash, digest) }
        if (index < 0) return@withLock ExposeConsumeResult.Denied(
            ExposeConsumeError.TOKEN_MISMATCH,
        )
        val record = records[index]
        if (record.action != action) {
            return@withLock ExposeConsumeResult.Denied(ExposeConsumeError.ACTION_MISMATCH)
        }
        if (channel != null && record.channel != channel) {
            return@withLock ExposeConsumeResult.Denied(ExposeConsumeError.CHANNEL_MISMATCH)
        }
        if (now >= record.expiresAtMillis) {
            return@withLock ExposeConsumeResult.Denied(ExposeConsumeError.EXPIRED)
        }
        if (record.consumedAtMillis != null) {
            return@withLock ExposeConsumeResult.Denied(ExposeConsumeError.ALREADY_CONSUMED)
        }
        store.save(records.toMutableList().apply {
            this[index] = record.copy(consumedAtMillis = now)
        })
        ExposeConsumeResult.Allowed
    }

    suspend fun revoke(channel: ExposeChannel) = mutex.withLock {
        store.save(store.load().filterNot { it.channel == channel })
    }

    private fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun constantTimeEquals(first: String, second: String): Boolean = MessageDigest.isEqual(
        first.encodeToByteArray(),
        second.encodeToByteArray(),
    )
}
