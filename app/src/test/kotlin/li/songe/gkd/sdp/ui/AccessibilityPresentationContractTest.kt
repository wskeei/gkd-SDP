package li.songe.gkd.sdp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-contract test fixing the accessibility presentation rules.
 *
 * These contract checks are replaced by behavioral UI tests on managed
 * devices (Task 28); they exist so regressions surface in plain JVM runs.
 */
class AccessibilityPresentationContractTest {
    @Test
    fun countdownPillNeverSetsALiveRegion() {
        val screen = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/Screen.kt",
        ).readText()
        val serviceHost = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/ServiceHost.kt",
        ).readText()
        assertFalse((screen + serviceHost).contains("liveRegion"))
        assertFalse((screen + serviceHost).contains("LiveRegion"))
        // the pill only updates its remaining-time text
        assertTrue(screen.contains("remainingText"))
    }

    @Test
    fun iconButtonsExposeNameActionAnd48dpTouchTarget() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AccessibleIconButton.kt",
        ).readText()
        assertTrue(source.contains("contentDescription: String"))
        assertTrue(source.contains("onClickLabel: String"))
        assertTrue(source.contains("touchTarget: Dp = DimensionTokens.MinTouchTarget"))
        assertTrue(source.contains(".size(touchTarget)"))
        assertTrue(source.contains("clearAndSetSemantics"))
    }

    @Test
    fun settingItemsMergeRowAndSwitchIntoOneSemanticAction() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SettingItem.kt",
        ).readText()
        assertTrue(source.contains("toggleable("))
        assertTrue(source.contains("Role.Switch"))
        assertTrue(source.contains("clearAndSetSemantics"))
    }

    @Test
    fun chartsAlwaysProvideSummaryTouchDetailAndDataTable() {
        val chart = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppDataChart.kt",
        ).readText()
        val policy = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppDataChart.kt",
        ).readText()
        assertTrue(chart.contains("summaryText"))
        assertTrue(chart.contains("pointDetailText"))
        assertTrue(chart.contains("查看数据"))
        assertTrue(policy.contains("AppDataTablePolicy"))
    }

    @Test
    fun inlineMessagesCarryIconAndTextNotJustColor() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/InlineMessage.kt",
        ).readText()
        assertTrue(source.contains("enum class InlineMessageKind"))
        assertTrue(source.contains("Icon("))
        assertTrue(source.contains("title: String"))
        assertTrue(source.contains("text: String? = null"))
    }

    @Test
    fun formFieldsKeepLabelsPermanent() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppFormField.kt",
        ).readText()
        assertTrue(source.contains("label = { Text(label) }"))
        assertTrue(source.contains("isError = errorText != null"))
    }

    @Test
    fun destructiveConfirmationUsesErrorColorAndExplicitObject() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppConfirmationDialog.kt",
        ).readText()
        assertTrue(source.contains("objectName"))
        assertTrue(source.contains("MaterialTheme.colorScheme.error"))
        assertTrue(source.contains("FullDeletionPolicy.FullDeletionPhrase"))
    }

    @Test
    fun touchTargetsAreAtLeast48dpAcrossSharedComponents() {
        val tokens = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/style/DimensionTokens.kt",
        ).readText()
        assertTrue(tokens.contains("val MinTouchTarget = 48.dp"))
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
