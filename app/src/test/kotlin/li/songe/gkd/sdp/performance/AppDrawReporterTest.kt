package li.songe.gkd.sdp.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawReporterTest {
    @Test
    fun reportsOnlyOnceForTheSameActivityInstance() {
        var reports = 0
        val reporter = AppDrawReporter { reports += 1 }
        reporter.reportInteractiveContent()
        reporter.reportInteractiveContent()
        assertEquals(1, reports)
        assertTrue(reporter.hasReported)
    }

    @Test
    fun recreationUsesANewActivityScopedReporter() {
        var firstReports = 0
        val first = AppDrawReporter { firstReports += 1 }
        var secondReports = 0
        val second = AppDrawReporter { secondReports += 1 }
        first.reportInteractiveContent()
        second.reportInteractiveContent()
        assertEquals(1, firstReports)
        assertEquals(1, secondReports)
    }
}
