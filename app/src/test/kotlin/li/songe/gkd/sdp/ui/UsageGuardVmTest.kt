package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardVmTest {
    @Test
    fun activeRecordCloseReasonsStayStable() {
        assertEquals(UsageGuardPolicy.GRANT_MODE_STRICT, 0)
        assertEquals(UsageGuardRecord.END_REASON_EXPIRED, 1)
        assertEquals(UsageGuardRecord.END_REASON_LEFT_APP, 2)
        assertEquals(UsageGuardRecord.END_REASON_HOME_BUTTON, 4)
    }
}
