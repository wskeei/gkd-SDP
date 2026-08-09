package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebOriginPolicyTest {
    @Test
    fun `main frame navigation has an exact scheme and host policy`() {
        assertEquals(WebNavigationDecision.ALLOW, WebOriginPolicy.decide("https://gkd.li/docs"))
        assertEquals(WebNavigationDecision.BLOCK, WebOriginPolicy.decide("https://gkd.li:444/docs"))
        assertEquals(WebNavigationDecision.INTERNAL, WebOriginPolicy.decide("gkd://settings"))
        assertEquals(WebNavigationDecision.EXTERNAL, WebOriginPolicy.decide("https://example.com"))
        listOf(
            "http://gkd.li",
            "file:///tmp/a",
            "content://authority/a",
            "data:text/html,test",
            "javascript:alert(1)",
            "intent://host",
            "ftp://example.com",
        ).forEach { url ->
            assertEquals(WebNavigationDecision.BLOCK, WebOriginPolicy.decide(url))
        }
    }

    @Test
    fun `mirror proxy accepts only fixed https package paths`() {
        assertTrue(
            WebOriginPolicy.isAllowedMirror(
                "https://registry.npmmirror.com/@gkd-kit/docs/1.0.0/files/index.html",
            ),
        )
        assertFalse(WebOriginPolicy.isAllowedMirror("http://registry.npmmirror.com/@gkd-kit/docs/a"))
        assertFalse(
            WebOriginPolicy.isAllowedMirror(
                "https://registry.npmmirror.com:444/@gkd-kit/docs/a",
            ),
        )
        assertFalse(WebOriginPolicy.isAllowedMirror("https://registry.npmmirror.com/other/a"))
        assertFalse(WebOriginPolicy.isAllowedMirror("https://evil.example/@gkd-kit/docs/a"))
    }
}
