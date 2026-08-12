package li.songe.gkd.sdp.remote

import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Interceptor
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ImagePreviewNetworkPolicyTest {
    @Test
    fun `https images are allowed`() {
        val decision = ImagePreviewNetworkPolicy.decideNetwork("https://example.com/path?q=1")
        assertTrue(decision.isAllowed)
    }

    @Test
    fun `http images are always rejected without authorization`() {
        val decision = ImagePreviewNetworkPolicy.decideNetwork("http://example.com/path")
        assertFalse(decision.isAllowed)
        assertEquals(ImagePreviewNetworkPolicy.ERROR_HTTP_BLOCKED, decision.errorCode)
    }

    @Test
    fun `network uris with unsupported schemes are rejected`() {
        listOf(
            "ftp://example.com/file",
            "ws://example.com/socket",
            "javascript:alert(1)",
            "data:text/plain,hello",
        ).forEach { uri ->
            val decision = ImagePreviewNetworkPolicy.decideNetwork(uri)
            assertFalse(decision.isAllowed)
            assertEquals(ImagePreviewNetworkPolicy.ERROR_INVALID_NETWORK_URI, decision.errorCode)
        }
    }

    @Test
    fun `malformed and userinfo https uris are rejected`() {
        assertFalse(ImagePreviewNetworkPolicy.decideNetwork("https://").isAllowed)
        assertFalse(ImagePreviewNetworkPolicy.decideNetwork("https://user:pass@example.com/path").isAllowed)
        assertFalse(ImagePreviewNetworkPolicy.decideNetwork("not a uri").isAllowed)
    }

    @Test
    fun `local uris remain displayable`() {
        assertTrue(ImagePreviewNetworkPolicy.isLocalUri("file:///tmp/example.png"))
        assertTrue(ImagePreviewNetworkPolicy.isLocalUri("content://media/external/images/1"))
        assertTrue(ImagePreviewNetworkPolicy.isLocalUri("android.resource://li.songe.gkd.sdp/raw/example"))
    }

    @Test
    fun `interceptor rejects http and http redirects before proceeding`() {
        val fakeChain = object : Interceptor.Chain {
            var proceeded = false
            override fun request(): Request = Request.Builder().url("http://example.com/a").build()
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
        val interceptor = ImagePreviewHttpsOnlyNetworkInterceptor()
        try {
            interceptor.intercept(fakeChain)
            throw AssertionError("expected HTTP image request to fail")
        } catch (_: IOException) {
            assertEquals(false, fakeChain.proceeded)
        }
    }

    @Test
    fun `interceptor allows https request`() {
        val fakeChain = object : Interceptor.Chain {
            var proceeded = false
            override fun request(): Request = Request.Builder().url("https://example.com/a").build()
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
        val interceptor = ImagePreviewHttpsOnlyNetworkInterceptor()
        interceptor.intercept(fakeChain)
        assertTrue(fakeChain.proceeded)
    }
}
