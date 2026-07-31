package li.songe.gkd.sdp.db

import org.junit.Assert.assertNotNull
import org.junit.Test

class DigitalSelfDisciplineLockDaoContractTest {
    @Test
    fun activeLockQueryContractExists() {
        assertNotNull(DigitalSelfDisciplineLockDao::hasAnyActiveLock)
    }
}
