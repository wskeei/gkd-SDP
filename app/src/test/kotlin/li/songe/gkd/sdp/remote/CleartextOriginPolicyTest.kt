package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextOriginPolicyTest {
    @Test
    fun `authorization key contains only canonical scheme host and port`() {
        assertEquals(
            "http://example.com:80",
            CleartextOriginPolicy.canonicalOrigin("HTTP://Example.COM/path?q=secret"),
        )
        assertEquals(
            "http://example.com:8080",
            CleartextOriginPolicy.canonicalOrigin("http://example.com:8080/a"),
        )
        assertNull(CleartextOriginPolicy.canonicalOrigin("http://user:pass@example.com/path"))
        assertNull(CleartextOriginPolicy.canonicalOrigin("file:///tmp/a"))
    }

    @Test
    fun `https is always allowed and every http redirect origin is independently authorized`() {
        val policy = CleartextOriginPolicy { setOf("http://allowed.example:80") }
        assertTrue(policy.isAllowed("https://fixed.example/path"))
        assertTrue(policy.isAllowed("http://allowed.example/first"))
        assertFalse(policy.isAllowed("http://redirect.example/second"))
        assertFalse(policy.isAllowed("javascript:alert(1)"))
    }
}
