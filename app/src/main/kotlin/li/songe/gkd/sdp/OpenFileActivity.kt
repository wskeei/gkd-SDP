package li.songe.gkd.sdp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.util.UriUtils
import li.songe.gkd.sdp.util.ZipUtils
import li.songe.gkd.sdp.util.createGkdTempDir
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

class OpenFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appScope.launchTry {
            val valid = withContext(Dispatchers.IO) { validateLegacyBackupIntent() }
            if (valid) {
                navToMainActivity()
            } else {
                toast(app.getString(R.string.s_a5b44b3b94))
                finish()
            }
        }
    }

    private fun validateLegacyBackupIntent(): Boolean {
        val sourceIntent = intent ?: return false
        val uri = sourceIntent.data ?: return false
        if (
            sourceIntent.action != Intent.ACTION_VIEW ||
            uri.scheme != "content" ||
            sourceIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0
        ) return false
        val tempDirectory = createGkdTempDir()
        return try {
            val archive = tempDirectory.resolve("legacy.zip")
            UriUtils.copyUriToFile(
                uri = uri,
                target = archive,
                maxBytes = ZipUtils.ArchiveLimits().maxArchiveBytes,
            )
            val magic = ByteArray(4)
            val magicLength = archive.inputStream().use { input -> input.read(magic) }
            magicLength == magic.size &&
                magic.contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)) &&
                runCatching { ZipUtils.validateArchive(archive) }.isSuccess
        } catch (_: Throwable) {
            false
        } finally {
            tempDirectory.deleteRecursively()
        }
    }
}
