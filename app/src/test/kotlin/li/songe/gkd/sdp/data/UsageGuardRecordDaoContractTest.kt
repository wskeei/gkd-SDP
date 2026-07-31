package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class UsageGuardRecordDaoContractTest {
    @Test
    fun latestRecordForAppContractExists() {
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::getLatestRecord)
    }
}
