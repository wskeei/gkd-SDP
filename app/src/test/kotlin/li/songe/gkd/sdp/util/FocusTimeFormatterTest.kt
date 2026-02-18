package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusTimeFormatterTest {
    @Test
    fun formatRemainingTextShowsSecondsWhenLessThanMinute() {
        val text = FocusTimeFormatter.formatRemainingText(endTime = 10_000L, now = 9_500L)
        assertEquals("剩余 1 秒", text)
    }

    @Test
    fun formatRemainingTextShowsMinuteAndSeconds() {
        val text = FocusTimeFormatter.formatRemainingText(endTime = 90_000L, now = 0L)
        assertEquals("剩余 1 分钟 30 秒", text)
    }

    @Test
    fun formatRemainingTextReturnsNullAfterExpired() {
        val text = FocusTimeFormatter.formatRemainingText(endTime = 1_000L, now = 1_000L)
        assertNull(text)
    }
}
