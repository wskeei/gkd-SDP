package li.songe.gkd.sdp.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateConsistencyTest {
    @Test
    fun privacyScreenUsesTheSharedContentStateContract() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataScreen.kt",
        ).readText()

        assertTrue(source.contains("ContentStateBox("))
        assertTrue(source.contains("ContentState.Loading"))
        assertTrue(source.contains("ContentState.Content"))
    }

    @Test
    fun reviewScreenUsesTypedLoadingReadyErrorStates() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/usagereview/UiState.kt",
        ).readText()

        assertTrue(source.contains("sealed interface DigitalSelfDisciplineReviewUiState"))
        assertTrue(source.contains("data object Loading"))
        assertTrue(source.contains("data class Ready"))
        assertTrue(source.contains("data class Error"))
    }

    @Test
    fun capabilityCenterUsesGraphResolutionAndSingleNextStep() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterScreen.kt",
        ).readText()

        assertTrue(source.contains("resolveCapabilityGraph("))
        assertTrue(source.contains("nextStep"))
    }

    private fun sourceFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var directory = File(userDir).absoluteFile
        while (!File(directory, "settings.gradle.kts").isFile || !File(directory, "app/src").isDirectory) {
            directory = directory.parentFile ?: error("Repository root marker not found from $userDir")
        }
        return File(directory, relativePath).also { check(it.isFile) { "Missing source: $relativePath" } }
    }
}
