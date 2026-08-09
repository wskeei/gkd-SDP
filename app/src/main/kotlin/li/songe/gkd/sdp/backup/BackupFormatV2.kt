package li.songe.gkd.sdp.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Serializable
data class BackupHeader(
    val formatVersion: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val nonce: String,
    val ciphertextLength: Long,
)

data class BackupEnvelope(
    val header: BackupHeader,
    val headerBytes: ByteArray,
    val ciphertext: ByteArray,
)

enum class BackupErrorCode {
    WEAK_PASSWORD,
    NONCE_REUSE,
    INVALID_MAGIC,
    MALFORMED_HEADER,
    TRUNCATED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_KDF,
    AUTHENTICATION_FAILED,
    INVALID_PAYLOAD,
    SCHEMA_MISMATCH,
    REFERENCE_MISMATCH,
    IMPORT_NOT_CONFIRMED,
    IMPORT_PREVIEW_STALE,
    IMPORT_FAILED,
    CRYPTO_FAILURE,
}

sealed interface BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>
    data class Failure(val code: BackupErrorCode) : BackupResult<Nothing>
}

object BackupFormatV2 {
    const val FORMAT_VERSION = 2
    const val MAGIC_TEXT = "GKDSDPBK2"
    private const val MAX_HEADER_BYTES = 4 * 1024
    private val magic = MAGIC_TEXT.encodeToByteArray()
    private val codec = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun canonicalHeaderBytes(header: BackupHeader): ByteArray =
        codec.encodeToString(header).encodeToByteArray()

    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)

    fun encode(header: BackupHeader, ciphertext: ByteArray): ByteArray {
        val headerBytes = canonicalHeaderBytes(header)
        require(headerBytes.size <= MAX_HEADER_BYTES)
        require(header.ciphertextLength == ciphertext.size.toLong())
        return ByteBuffer.allocate(magic.size + Int.SIZE_BYTES + headerBytes.size + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(magic)
            .putInt(headerBytes.size)
            .put(headerBytes)
            .put(ciphertext)
            .array()
    }

    fun parse(bytes: ByteArray): BackupResult<BackupEnvelope> {
        val prefixSize = magic.size + Int.SIZE_BYTES
        if (bytes.size < prefixSize) return BackupResult.Failure(BackupErrorCode.TRUNCATED)
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            return BackupResult.Failure(BackupErrorCode.INVALID_MAGIC)
        }
        val headerLength = ByteBuffer.wrap(bytes, magic.size, Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        if (headerLength <= 0 || headerLength > MAX_HEADER_BYTES) {
            return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
        }
        val ciphertextOffset = prefixSize + headerLength
        if (ciphertextOffset > bytes.size) {
            return BackupResult.Failure(BackupErrorCode.TRUNCATED)
        }
        val headerBytes = bytes.copyOfRange(prefixSize, ciphertextOffset)
        val header = runCatching {
            codec.decodeFromString<BackupHeader>(headerBytes.decodeToString())
        }.getOrElse {
            return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
        }
        if (header.ciphertextLength < 0) {
            return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
        }
        val actualCiphertextLength = bytes.size.toLong() - ciphertextOffset
        if (actualCiphertextLength < header.ciphertextLength) {
            return BackupResult.Failure(BackupErrorCode.TRUNCATED)
        }
        if (actualCiphertextLength != header.ciphertextLength) {
            return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
        }
        return BackupResult.Success(
            BackupEnvelope(
                header = header,
                headerBytes = headerBytes,
                ciphertext = bytes.copyOfRange(ciphertextOffset, bytes.size),
            ),
        )
    }
}
