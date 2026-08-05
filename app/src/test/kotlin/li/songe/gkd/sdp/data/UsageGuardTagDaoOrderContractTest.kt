package li.songe.gkd.sdp.data

import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardTagDaoOrderContractTest {
    @Test
    fun queryAllKeepsOtherLastAndOrderStable() {
        val normalizedSql = UsageGuardTag.QUERY_ALL_SQL
            .replace(Regex("\\s+"), " ")
            .trim()

        assertTrue(
            normalizedSql.contains(
                "ORDER BY CASE WHEN TRIM(name) = '其他' THEN 1 ELSE 0 END ASC, " +
                    "is_preset DESC, created_at ASC, id ASC",
            ),
        )
    }
}
