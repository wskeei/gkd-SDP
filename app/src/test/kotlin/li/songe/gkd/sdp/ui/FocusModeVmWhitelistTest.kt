package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusModeVmWhitelistTest {
    @Test
    fun ensureSelfWhitelisted_addsSelfWhenMissing() {
        val result = FocusModeVm.ensureSelfWhitelisted(
            packages = listOf("com.example.app"),
            selfPackage = "li.songe.gkd.sdp"
        )

        assertEquals(listOf("li.songe.gkd.sdp", "com.example.app"), result)
    }

    @Test
    fun ensureSelfWhitelisted_keepsListWhenAlreadyContainsSelf() {
        val result = FocusModeVm.ensureSelfWhitelisted(
            packages = listOf("li.songe.gkd.sdp", "com.example.app"),
            selfPackage = "li.songe.gkd.sdp"
        )

        assertEquals(listOf("li.songe.gkd.sdp", "com.example.app"), result)
    }
}
