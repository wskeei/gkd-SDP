package li.songe.gkd.sdp.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDurationPresentationTest {
    @Test
    fun durationsUseSecondsMinutesHoursAndDays() {
        assertEquals("45秒", UsageDurationPresentation.formatDuration(45_000L))
        assertEquals("2分 05秒", UsageDurationPresentation.formatDuration(125_000L))
        assertEquals("3小时 04分", UsageDurationPresentation.formatDuration((3 * 60 + 4) * 60_000L))
        assertEquals("2天 03小时", UsageDurationPresentation.formatDuration(2 * 86_400_000L + 3 * 3_600_000L))
    }

    @Test
    fun zeroAndNegativeDurationsStayBounded() {
        assertEquals("0秒", UsageDurationPresentation.formatDuration(0L))
        assertEquals("0秒", UsageDurationPresentation.formatDuration(-1L))
    }

    @Test
    fun ratioFormattingUsesRhythmPolicy() {
        assertEquals("4.0", UsageDurationPresentation.formatRatio(4.0))
        assertEquals("<0.01", UsageDurationPresentation.formatRatio(0.001))
        assertEquals("—", UsageDurationPresentation.formatRatio(null))
    }

    @Test
    fun deltaIsAbsolute() {
        assertEquals(1.5, UsageDurationPresentation.ratioDelta(2.5, 1.0), 0.0001)
        assertEquals(1.5, UsageDurationPresentation.ratioDelta(1.0, 2.5), 0.0001)
    }
}
