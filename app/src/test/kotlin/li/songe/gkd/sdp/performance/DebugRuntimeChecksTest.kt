package li.songe.gkd.sdp.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRuntimeChecksTest {
    @Test
    fun syntheticViolationReachesTheInjectedListener() {
        val events = mutableListOf<DebugRuntimeEvent>()
        DebugRuntimeChecks.enable(
            listener = DebugRuntimeViolationListener { events += it },
            installPolicies = false,
        )
        DebugRuntimeChecks.report(
            DebugRuntimeEvent(
                violation = DebugRuntimeViolation.CLEARTEXT_NETWORK,
                detail = "synthetic",
            ),
        )
        assertEquals(1, events.size)
        assertEquals(DebugRuntimeViolation.CLEARTEXT_NETWORK, events.single().violation)
        assertTrue(events.single().detail.isNotBlank())
    }
}
