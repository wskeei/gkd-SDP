package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

class CleartextOriginPolicyTest {
    @Test
    fun objectAuthorizationsCanBeGrantedAndRevokedInMemory() {
        val store = MutableStateFlow<Set<String>>(emptySet())
        CleartextOriginAuthorizations.testOrigins = store
        try {
            val origin = CleartextOriginAuthorizations.authorize("HTTP://Example.COM/a")
            assertEquals("http://example.com:80", origin)
            assertEquals(setOf("http://example.com:80"), store.value)
            assertTrue(
                CleartextOriginAuthorizations.policy.isAllowed("http://example.com/a"),
            )

            CleartextOriginAuthorizations.revoke("http://example.com:80")
            assertEquals(emptySet<String>(), store.value)
            assertFalse(
                CleartextOriginAuthorizations.policy.isAllowed("http://example.com/a"),
            )
            assertEquals(
                null,
                CleartextOriginAuthorizations.authorize("file:///tmp/image.png"),
            )
        } finally {
            CleartextOriginAuthorizations.testOrigins = null
        }
    }

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
        assertNull(CleartextOriginPolicy.canonicalOrigin("http://example.com:99999/path"))
        assertNull(CleartextOriginPolicy.canonicalOrigin("http://"))
        assertNull(CleartextOriginPolicy.canonicalOrigin("not a url"))
        assertNull(CleartextOriginPolicy.canonicalOrigin("file:///tmp/a"))
    }

    @Test
    fun `https is always allowed and every http redirect origin is independently authorized`() {
        val policy = CleartextOriginPolicy { setOf("http://allowed.example:80") }
        assertTrue(policy.isAllowed("https://fixed.example/path"))
        assertTrue(policy.isAllowed("http://allowed.example/first"))
        assertFalse(policy.isAllowed("http://redirect.example/second"))
        assertFalse(policy.isAllowed("https://"))
        assertFalse(policy.isAllowed("https://user:pass@fixed.example/path"))
        assertFalse(policy.isAllowed("http://user:pass@allowed.example/path"))
        assertFalse(policy.isAllowed("http://allowed.example:99999/path"))
        assertFalse(policy.isAllowed("http://"))
        assertFalse(policy.isAllowed("javascript:alert(1)"))
    }

    @Test
    fun interceptorRejectsUnauthorizedOriginBeforeProceeding() {
        val interceptor = CleartextOriginInterceptor(
            CleartextOriginPolicy { setOf("http://allowed.example:80") },
        )
        val chain = object : Interceptor.Chain {
            var proceeded = false
            override fun request(): Request =
                Request.Builder().url("http://denied.example/a").build()

            override fun proceed(request: Request): Response {
                proceeded = true
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun readTimeoutMillis() = 0
            override fun writeTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
        }

        try {
            interceptor.intercept(chain)
            throw AssertionError("expected unauthorized origin to fail")
        } catch (_: IOException) {
            assertFalse(chain.proceeded)
        }
    }

    @Test
    fun interceptorAllowsAuthorizedOrigin() {
        val interceptor = CleartextOriginInterceptor(
            CleartextOriginPolicy { setOf("http://allowed.example:80") },
        )
        val request = Request.Builder().url("http://allowed.example/a").build()
        val chain = object : Interceptor.Chain {
            var proceededRequest: Request? = null
            override fun request(): Request = request

            override fun proceed(request: Request): Response {
                proceededRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun readTimeoutMillis() = 0
            override fun writeTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
                throw UnsupportedOperationException()
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(request, chain.proceededRequest)
    }

}
