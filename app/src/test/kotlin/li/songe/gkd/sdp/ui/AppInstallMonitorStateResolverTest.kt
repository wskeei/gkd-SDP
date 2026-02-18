package li.songe.gkd.sdp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInstallMonitorStateResolverTest {

    @Test
    fun `db snapshot should override stale cache when package already uninstalled`() {
        val monitoredInstalledMap = mapOf("com.demo.app" to false)
        val cachedInstalledPackages = setOf("com.demo.app")

        assertFalse(
            resolveCurrentInstallState(
                packageName = "com.demo.app",
                monitoredInstalledMap = monitoredInstalledMap,
                cachedInstalledPackages = cachedInstalledPackages
            )
        )
    }

    @Test
    fun `cache should be fallback when package missing from db snapshot`() {
        val monitoredInstalledMap = emptyMap<String, Boolean>()
        val cachedInstalledPackages = setOf("com.demo.app")

        assertTrue(
            resolveCurrentInstallState(
                packageName = "com.demo.app",
                monitoredInstalledMap = monitoredInstalledMap,
                cachedInstalledPackages = cachedInstalledPackages
            )
        )
    }
}
