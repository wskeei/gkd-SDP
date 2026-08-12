package li.songe.gkd.sdp.lint

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.CURRENT_API
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdpIssueRegistryTest {
    @Test
    fun registryExposesHardcodedTextWithCurrentApi() {
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
        val registry = SdpIssueRegistry()
        assertEquals(CURRENT_API, registry.api)
        assertTrue(registry.issues.any { it.id == "HardcodedText" })
    }
}
