package li.songe.gkd.sdp.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRequestLayoutContractTest {
    @Test
    fun requestFormKeepsElapsedInsightAboveTagsAndRatioInsideDurationSection() {
        val source = File(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt",
        ).readText()
        val form = source.substringAfter("private fun UsageGuardRequestContent(")

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
}
