package li.songe.gkd.sdp.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupCryptoTest {
    @Test
    fun `v2 envelope uses fixed authenticated format and clears passwords`() {
        val crypto = BackupCrypto()
        val password = "correct horse battery staple".toCharArray()
        val encrypted = crypto.encrypt("payload".encodeToByteArray(), password)

        assertTrue(password.all { it == '\u0000' })
        val success = encrypted as BackupResult.Success
        assertArrayEquals("GKDSDPBK2".encodeToByteArray(), success.value.copyOfRange(0, 9))
        val envelope = BackupFormatV2.parse(success.value) as BackupResult.Success
        assertEquals(2, envelope.value.header.formatVersion)
        assertEquals("PBKDF2WithHmacSHA256", envelope.value.header.kdf)
        assertEquals(600_000, envelope.value.header.iterations)
        assertEquals(16, Base64.getDecoder().decode(envelope.value.header.salt).size)
        assertEquals(12, Base64.getDecoder().decode(envelope.value.header.nonce).size)
        assertEquals(7L + 16L, envelope.value.header.ciphertextLength)
        assertEquals(
            setOf(
                "formatVersion",
                "kdf",
                "iterations",
                "salt",
                "nonce",
                "ciphertextLength",
            ),
            Json.encodeToJsonElement(
                BackupHeader.serializer(),
                envelope.value.header,
            ).jsonObject.keys,
        )

        val decryptPassword = "correct horse battery staple".toCharArray()
        val decrypted = crypto.decrypt(success.value, decryptPassword) as BackupResult.Success
        assertArrayEquals("payload".encodeToByteArray(), decrypted.value)
        assertTrue(decryptPassword.all { it == '\u0000' })
    }

    @Test
    fun `password minimum counts Unicode code points and always clears input`() {
        val weakPassword = "😀😀😀😀😀😀".toCharArray()
        val result = BackupCrypto().encrypt(ByteArray(0), weakPassword)

        assertEquals(BackupErrorCode.WEAK_PASSWORD, (result as BackupResult.Failure).code)
        assertTrue(weakPassword.all { it == '\u0000' })
    }

    @Test
    fun `wrong password and tampering return stable authentication errors`() {
        val crypto = BackupCrypto()
        val encrypted = (crypto.encrypt(
            "sensitive payload".encodeToByteArray(),
            "correct-password".toCharArray(),
        ) as BackupResult.Success).value

        val wrongPassword = crypto.decrypt(encrypted, "incorrect-password".toCharArray())
        assertEquals(
            BackupErrorCode.AUTHENTICATION_FAILED,
            (wrongPassword as BackupResult.Failure).code,
        )

        val parsed = (BackupFormatV2.parse(encrypted) as BackupResult.Success).value
        val changedSalt = Base64.getEncoder().encodeToString(ByteArray(16) { 7 })
        val changedHeader = parsed.header.copy(salt = changedSalt)
        val changedHeaderEnvelope = BackupFormatV2.encode(changedHeader, parsed.ciphertext)
        val headerResult = crypto.decrypt(
            changedHeaderEnvelope,
            "correct-password".toCharArray(),
        )
        assertEquals(
            BackupErrorCode.AUTHENTICATION_FAILED,
            (headerResult as BackupResult.Failure).code,
        )

        val changedCiphertext = encrypted.copyOf().apply { this[lastIndex] = this[lastIndex].inc() }
        val ciphertextResult = crypto.decrypt(
            changedCiphertext,
            "correct-password".toCharArray(),
        )
        assertEquals(
            BackupErrorCode.AUTHENTICATION_FAILED,
            (ciphertextResult as BackupResult.Failure).code,
        )
    }

    @Test
    fun `truncated and unsupported envelopes return stable format errors`() {
        val crypto = BackupCrypto()
        val encrypted = (crypto.encrypt(
            "payload".encodeToByteArray(),
            "correct-password".toCharArray(),
        ) as BackupResult.Success).value

        val truncated = crypto.decrypt(
            encrypted.copyOf(encrypted.size - 3),
            "correct-password".toCharArray(),
        )
        assertEquals(BackupErrorCode.TRUNCATED, (truncated as BackupResult.Failure).code)

        val parsed = (BackupFormatV2.parse(encrypted) as BackupResult.Success).value
        val unsupported = BackupFormatV2.encode(
            parsed.header.copy(formatVersion = 3),
            parsed.ciphertext,
        )
        val unsupportedResult = crypto.decrypt(
            unsupported,
            "correct-password".toCharArray(),
        )
        assertEquals(
            BackupErrorCode.UNSUPPORTED_VERSION,
            (unsupportedResult as BackupResult.Failure).code,
        )
    }

    @Test
    fun `nonce reuse is rejected before encryption`() {
        val random = BackupRandomSource { size -> ByteArray(size) { 4 } }
        val crypto = BackupCrypto(random)

        assertTrue(
            crypto.encrypt(ByteArray(0), "first-password".toCharArray()) is BackupResult.Success,
        )
        val repeated = crypto.encrypt(ByteArray(0), "second-password".toCharArray())

        assertEquals(BackupErrorCode.NONCE_REUSE, (repeated as BackupResult.Failure).code)
    }

    @Test
    fun `unsupported kdf and malformed header fields return stable errors`() {
        val crypto = BackupCrypto()
        val encrypted = (crypto.encrypt(
            "payload".encodeToByteArray(),
            "correct-password".toCharArray(),
        ) as BackupResult.Success).value
        val envelope = (BackupFormatV2.parse(encrypted) as BackupResult.Success).value

        val changedKdf = BackupFormatV2.encode(
            envelope.header.copy(kdf = "PBKDF2WithHmacSHA1"),
            envelope.ciphertext,
        )
        assertEquals(
            BackupErrorCode.UNSUPPORTED_KDF,
            (crypto.decrypt(changedKdf, "correct-password".toCharArray()) as BackupResult.Failure).code,
        )

        val changedIterations = BackupFormatV2.encode(
            envelope.header.copy(iterations = 1),
            envelope.ciphertext,
        )
        assertEquals(
            BackupErrorCode.UNSUPPORTED_KDF,
            (crypto.decrypt(changedIterations, "correct-password".toCharArray()) as BackupResult.Failure).code,
        )

        val malformedSalt = BackupFormatV2.encode(
            envelope.header.copy(salt = "short"),
            envelope.ciphertext,
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (crypto.decrypt(malformedSalt, "correct-password".toCharArray()) as BackupResult.Failure).code,
        )

        val malformedNonce = BackupFormatV2.encode(
            envelope.header.copy(nonce = "short"),
            envelope.ciphertext,
        )
        assertEquals(
            BackupErrorCode.MALFORMED_HEADER,
            (crypto.decrypt(malformedNonce, "correct-password".toCharArray()) as BackupResult.Failure).code,
        )
    }

    @Test
    fun `wrong random lengths fail closed and clear password`() {
        val crypto = BackupCrypto(
            BackupRandomSource { size -> ByteArray(size - 1) },
        )
        val password = "correct-password".toCharArray()

        val result = crypto.encrypt(ByteArray(0), password)

        assertEquals(BackupErrorCode.CRYPTO_FAILURE, (result as BackupResult.Failure).code)
        assertTrue(password.all { it == '\u0000' })
    }
}
