package li.songe.gkd.sdp.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRequestLayoutContractTest {
    @Test
    fun requestFormKeepsElapsedInsightAboveTagsAndRatioInsideDurationSection() {
        val screen = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/Screen.kt").readText()
        val sections = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/Sections.kt").readText()
        val source = screen + "\n" + sections
        val form = source.substringAfter("internal fun UsageGuardRequestContent(")

        val elapsed = form.indexOf("SelfControlElapsedCard(")
        val tags = form.indexOf("Text(\"选择标签\"")
        val reason = form.indexOf("label = { Text(\"申请理由\") }")
        val duration = form.indexOf("Text(\"申请时长\"")
        val ratio = form.indexOf("UsageDurationRatioFeedback(")
        val submit = form.indexOf("Text(\"开始使用\")")
        val cancel = form.indexOf("Text(\"取消\")")

        assertTrue(elapsed >= 0)
        assertTrue(elapsed < tags)
        assertTrue(tags < reason)
        assertTrue(reason < duration)
        assertTrue(duration < ratio)
        assertTrue(ratio < submit)
        assertTrue(submit < cancel)
        assertFalse(form.contains("UsageRequestRhythmSummary"))
    }

    @Test
    fun requestFormConsumesImeInsetsAndRelocatesEveryInputField() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/Screen.kt",
        ).readText()
        val sections = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/Sections.kt",
        ).readText()
        val form = (source + "\n" + sections).substringAfter("internal fun UsageGuardRequestContent(")
        val windowParams = source
            .plus(sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/ServiceHost.kt").readText())
            .substringAfter("val params = WindowManager.LayoutParams(")
            .substringBefore("runCatching { windowManager.addView")

        val imePadding = form.indexOf(".imePadding()")
        val verticalScroll = form.indexOf(".verticalScroll(formScrollState)")
        assertTrue(imePadding >= 0)
        assertTrue(imePadding < verticalScroll)

        assertTrue(form.contains("val newTagInputModifier = rememberImeAwareBringIntoViewModifier()"))
        assertTrue(form.contains("val reasonInputModifier = rememberImeAwareBringIntoViewModifier()"))
        assertTrue(form.contains("val customDurationInputModifier = rememberImeAwareBringIntoViewModifier()"))
        assertTrue(form.contains(".then(newTagInputModifier)"))
        assertTrue(form.contains(".then(reasonInputModifier)"))
        assertTrue(form.contains(".then(customDurationInputModifier)"))

        assertTrue(source.contains("val imeVisible = WindowInsets.isImeVisible"))
        assertTrue(source.contains("LaunchedEffect(isFocused, imeVisible)"))
        assertTrue(source.contains("requester.bringIntoView()"))

        assertTrue(windowParams.contains("USAGE_GUARD_REQUEST_OVERLAY_FLAGS"))
        assertTrue(
            windowParams.contains(
                "softInputMode = USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE",
            ),
        )
        assertFalse(windowParams.contains("FLAG_LAYOUT_NO_LIMITS"))
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
