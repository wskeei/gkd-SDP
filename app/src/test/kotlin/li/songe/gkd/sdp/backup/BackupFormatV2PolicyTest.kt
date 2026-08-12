package li.songe.gkd.sdp.backup

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import li.songe.gkd.sdp.util.json
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BackupFormatV2PolicyTest {
    @Test
    fun parseRejectsTruncatedAndWrongMagic() {
        assertEquals(
            BackupErrorCode.TRUNCATED,
            (BackupFormatV2.parse(ByteArray(0)) as BackupResult.Failure).code,
        )
        assertEquals(
            BackupErrorCode.TRUNCATED,
            (BackupFormatV2.parse(BackupFormatV2.MAGIC_TEXT.encodeToByteArray()) as BackupResult.Failure).code,
        )
        assertEquals(
            BackupErrorCode.INVALID_MAGIC,
            (BackupFormatV2.parse("WRONG-PREFIX!!!".encodeToByteArray()) as BackupResult.Failure).code,
        )
    }

    @Test
    fun parseRejectsInvalidHeaderLengthAndTruncatedPayload() {
        val header = BackupHeader(
            formatVersion = 2,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 1,
            salt = "salt",
            nonce = "nonce",
            ciphertextLength = 1,
        )
        val headerBytes = json.encodeToString(header).encodeToByteArray()

        val badLength = encode(headerLength = -1, headerBytes = headerBytes, ciphertext = ByteArray(1))
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (BackupFormatV2.parse(badLength) as BackupResult.Failure).code,
        )

        val tooLargeLength = encode(
            headerLength = 5_000,
            headerBytes = headerBytes,
            ciphertext = ByteArray(1),
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (BackupFormatV2.parse(tooLargeLength) as BackupResult.Failure).code,
        )

        val truncated = encode(headerLength = 1_000, headerBytes = headerBytes, ciphertext = ByteArray(1))
        assertEquals(
            BackupErrorCode.TRUNCATED,
            (BackupFormatV2.parse(truncated) as BackupResult.Failure).code,
        )
    }

    @Test
    fun parseRejectsMalformedHeaderAndLengthMismatch() {
        val invalidJson = encode(
            headerLength = 4,
            headerBytes = "!!!!".encodeToByteArray(),
            ciphertext = ByteArray(0),
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (BackupFormatV2.parse(invalidJson) as BackupResult.Failure).code,
        )

        val negativeLength = BackupHeader(
            formatVersion = 2,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 1,
            salt = "salt",
            nonce = "nonce",
            ciphertextLength = -1,
        )
        val negativeHeaderBytes = json.encodeToString(negativeLength).encodeToByteArray()
        val negativeEnvelope = encode(
            headerLength = negativeHeaderBytes.size,
            headerBytes = negativeHeaderBytes,
            ciphertext = ByteArray(0),
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (BackupFormatV2.parse(negativeEnvelope) as BackupResult.Failure).code,
        )

        val declaredOne = BackupHeader(
            formatVersion = 2,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 1,
            salt = "salt",
            nonce = "nonce",
            ciphertextLength = 1,
        )
        val declaredBytes = json.encodeToString(declaredOne).encodeToByteArray()
        val extra = encode(
            headerLength = declaredBytes.size,
            headerBytes = declaredBytes,
            ciphertext = ByteArray(2),
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (BackupFormatV2.parse(extra) as BackupResult.Failure).code,
        )
    }

    @Test
    fun encodeRequiresDeclaredCiphertextLength() {
        val header = BackupHeader(
            formatVersion = 2,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 1,
            salt = "salt",
            nonce = "nonce",
            ciphertextLength = 1,
        )
        assertThrows { BackupFormatV2.encode(header, ByteArray(2)) }
    }

    @Test
    fun canonicalHeaderBytesAndEncodeRoundTrip() {
        val header = BackupHeader(
            formatVersion = 2,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 1,
            salt = "salt",
            nonce = "nonce",
            ciphertextLength = 3,
        )
        val bytes = BackupFormatV2.encode(header, byteArrayOf(1, 2, 3))
        val parsed = BackupFormatV2.parse(bytes)
        assertEquals(header.salt, (parsed as BackupResult.Success).value.header.salt)
    }

    private fun encode(
        headerLength: Int,
        headerBytes: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val magic = BackupFormatV2.MAGIC_TEXT.encodeToByteArray()
        return ByteBuffer.allocate(magic.size + Int.SIZE_BYTES + headerBytes.size + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(magic)
            .putInt(headerLength)
            .put(headerBytes)
            .put(ciphertext)
            .array()
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }
}
