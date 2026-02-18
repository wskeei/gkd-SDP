package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class AutoReenableDaoContractTest {
    @Test
    fun daoBulkEnableContractsExist() {
        assertNotNull(SubsItem.SubsItemDao::enableAllDisabled)
        assertNotNull(AppGroup.AppGroupDao::enableAllDisabled)
        assertNotNull(BlockTimeRule.BlockTimeRuleDao::enableAllDisabled)
        assertNotNull(UrlRuleGroup.UrlRuleGroupDao::enableAllDisabled)
        assertNotNull(UrlBlockRule.UrlBlockRuleDao::enableAllDisabled)
        assertNotNull(UrlTimeRule.UrlTimeRuleDao::enableAllDisabled)
    }
}
