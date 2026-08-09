package li.songe.gkd.sdp.ui.style

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeActivityOwnerContractTest {
    @Test
    fun localizedContextKeepsTheOriginalActivityOwner() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/style/Theme.kt",
        ).readText()
        val provider = source
            .substringAfter("CompositionLocalProvider(")
            .substringBefore(") {")

        assertTrue(provider.contains("LocalContext provides localizedContext"))
        assertTrue(provider.contains("LocalActivity provides activity"))
    }

    @Test
    fun appThemeDoesNotOwnTheProcessLocale() {
        val themeSource = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/style/Theme.kt",
        ).readText()
        val preferenceSource = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/store/DisplayPreferenceBackup.kt",
        ).readText()

        assertFalse(themeSource.contains("Locale.setDefault"))
        assertTrue(preferenceSource.contains("processLocaleCoordinatorStarted"))
        assertTrue(preferenceSource.contains(".collect(Locale::setDefault)"))
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }
}
