package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.backup.BackupCrypto
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.backup.BackupRandomSource
import li.songe.gkd.sdp.backup.BackupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupFlowTest {
    @Test
    fun encryptedBackupRoundTripsAndRejectsWrongPassword() {
        val crypto = BackupCrypto(
            randomSource = BackupRandomSource { size -> ByteArray(size) { index -> (index + 1).toByte() } },
        )
        val encryptPassword = "correct horse battery staple".toCharArray()
        val decryptPassword = "correct horse battery staple".toCharArray()

        val encrypted = crypto.encrypt("local-only backup".encodeToByteArray(), encryptPassword)
        assertTrue(encrypted is BackupResult.Success)
        val decrypted = crypto.decrypt((encrypted as BackupResult.Success).value, decryptPassword)
        assertEquals(
            "local-only backup",
            (decrypted as BackupResult.Success).value.decodeToString(),
        )

        val wrong = crypto.decrypt(
            encrypted.value,
            "wrong password here".toCharArray(),
        )
        assertEquals(BackupErrorCode.AUTHENTICATION_FAILED, (wrong as BackupResult.Failure).code)
    }

    @Test
    fun weakPasswordIsRejectedBeforeEncryption() {
        val crypto = BackupCrypto()
        val result = crypto.encrypt("x".encodeToByteArray(), "short".toCharArray())
        assertEquals(BackupErrorCode.WEAK_PASSWORD, (result as BackupResult.Failure).code)
    }
}
