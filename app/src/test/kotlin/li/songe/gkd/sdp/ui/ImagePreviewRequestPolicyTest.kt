package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.remote.ImagePreviewNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePreviewRequestPolicyTest {
    @Test
    fun `foreground request policy allows local and https only`() {
        assertTrue(ImagePreviewNetworkPolicy.isDisplayAllowed("file:///tmp/example.png"))
        assertTrue(ImagePreviewNetworkPolicy.isDisplayAllowed("https://example.com/example.png"))
        assertFalse(ImagePreviewNetworkPolicy.isDisplayAllowed("http://example.com/example.png"))
    }
}
