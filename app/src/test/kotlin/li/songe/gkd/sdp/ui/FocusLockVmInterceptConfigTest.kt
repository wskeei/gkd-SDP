package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.InterceptConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FocusLockVmInterceptConfigTest {
    @Test
    fun latestInterceptConfigByKey_usesNewestRowForSameRule() {
        val older = InterceptConfig(
            id = 3,
            subsId = 1L,
            appId = "com.demo",
            groupKey = 100,
            enabled = true,
            cooldownSeconds = 10,
            message = "old"
        )
        val newer = older.copy(id = 8, enabled = false, message = "new")

        val latest = FocusLockVm.latestInterceptConfigByKey(listOf(newer, older))
        val picked = latest[Triple(1L, "com.demo", 100)]

        assertNotNull(picked)
        assertFalse(picked!!.enabled)
        assertEquals("new", picked.message)
    }
}
