package li.songe.gkd.sdp.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

fun interface BackupRandomSource {
    fun nextBytes(size: Int): ByteArray
}

class BackupCrypto(
    private val randomSource: BackupRandomSource = SecureRandom().let { secureRandom ->
        BackupRandomSource { size -> ByteArray(size).also(secureRandom::nextBytes) }
    },
) {
    companion object {
        const val KDF_NAME = "PBKDF2WithHmacSHA256"
        const val KDF_ITERATIONS = 600_000
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val MIN_PASSWORD_CODE_POINTS = 12
    }

    private val usedNonces = mutableSetOf<String>()

    fun encrypt(payload: ByteArray, password: CharArray): BackupResult<ByteArray> {
        var keyBytes: ByteArray? = null
        return try {
            if (password.codePointCount() < MIN_PASSWORD_CODE_POINTS) {
                return BackupResult.Failure(BackupErrorCode.WEAK_PASSWORD)
            }
            val salt = randomSource.nextBytes(SALT_BYTES)
            val nonce = randomSource.nextBytes(NONCE_BYTES)
            if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES) {
                return BackupResult.Failure(BackupErrorCode.CRYPTO_FAILURE)
            }
            val nonceKey = Base64.getEncoder().encodeToString(nonce)
            synchronized(usedNonces) {
                if (!usedNonces.add(nonceKey)) {
                    return BackupResult.Failure(BackupErrorCode.NONCE_REUSE)
                }
            }
            keyBytes = deriveKey(password, salt)
            val header = BackupHeader(
                formatVersion = BackupFormatV2.FORMAT_VERSION,
                kdf = KDF_NAME,
                iterations = KDF_ITERATIONS,
                salt = Base64.getEncoder().encodeToString(salt),
                nonce = nonceKey,
                ciphertextLength = payload.size.toLong() + GCM_TAG_BITS / Byte.SIZE_BITS,
            )
            val headerBytes = BackupFormatV2.canonicalHeaderBytes(header)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(headerBytes)
            BackupResult.Success(BackupFormatV2.encode(header, cipher.doFinal(payload)))
        } catch (_: Throwable) {
            BackupResult.Failure(BackupErrorCode.CRYPTO_FAILURE)
        } finally {
            keyBytes?.fill(0)
            password.fill('\u0000')
        }
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): BackupResult<ByteArray> {
        var keyBytes: ByteArray? = null
        return try {
            if (password.codePointCount() < MIN_PASSWORD_CODE_POINTS) {
                return BackupResult.Failure(BackupErrorCode.WEAK_PASSWORD)
            }
            val envelope = when (val parsed = BackupFormatV2.parse(encrypted)) {
                is BackupResult.Failure -> return parsed
                is BackupResult.Success -> parsed.value
            }
            val header = envelope.header
            if (header.formatVersion != BackupFormatV2.FORMAT_VERSION) {
                return BackupResult.Failure(BackupErrorCode.UNSUPPORTED_VERSION)
            }
            if (header.kdf != KDF_NAME || header.iterations != KDF_ITERATIONS) {
                return BackupResult.Failure(BackupErrorCode.UNSUPPORTED_KDF)
            }
            val salt = decodeExact(header.salt, SALT_BYTES)
                ?: return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
            val nonce = decodeExact(header.nonce, NONCE_BYTES)
                ?: return BackupResult.Failure(BackupErrorCode.MALFORMED_HEADER)
            keyBytes = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(envelope.headerBytes)
            BackupResult.Success(cipher.doFinal(envelope.ciphertext))
        } catch (_: AEADBadTagException) {
            BackupResult.Failure(BackupErrorCode.AUTHENTICATION_FAILED)
        } catch (_: Throwable) {
            BackupResult.Failure(BackupErrorCode.CRYPTO_FAILURE)
        } finally {
            keyBytes?.fill(0)
            password.fill('\u0000')
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeExact(value: String, expectedBytes: Int): ByteArray? = runCatching {
        Base64.getDecoder().decode(value).takeIf { it.size == expectedBytes }
    }.getOrNull()

    private fun CharArray.codePointCount(): Int {
        var count = 0
        var index = 0
        while (index < size) {
            val current = this[index]
            index += if (
                Character.isHighSurrogate(current) &&
                index + 1 < size &&
                Character.isLowSurrogate(this[index + 1])
            ) {
                2
            } else {
                1
            }
            count++
        }
        return count
    }
}
