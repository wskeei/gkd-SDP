package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class SelfControlAttemptDaoContractTest {
    @Test
    fun recordAndGetPreviousContractExists() {
        assertNotNull(SelfControlAttempt.SelfControlAttemptDao::recordAndGetPrevious)
    }
}
