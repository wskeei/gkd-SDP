package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportRepositoryTest {
    @Test
    fun publishThenConsumeIsOneShot() {
        CrashReportRepository.publish(emptyList())
        assertTrue(CrashReportRepository.consume().isEmpty())
        CrashReportRepository.publish(emptyList())
        assertTrue(CrashReportRepository.consume().isEmpty())
    }

    @Test
    fun consumeClearsPendingPayload() {
        CrashReportRepository.publish(emptyList())
        assertEquals(0, CrashReportRepository.consume().size)
        assertEquals(0, CrashReportRepository.consume().size)
    }
}
