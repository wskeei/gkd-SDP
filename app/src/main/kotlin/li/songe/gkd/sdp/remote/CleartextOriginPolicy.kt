package li.songe.gkd.sdp.remote

import androidx.compose.runtime.Stable
import li.songe.gkd.sdp.store.createAnyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URI

@Stable
class CleartextOriginPolicy(
    private val authorizedOrigins: () -> Set<String>,
) {
    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> uri.host?.isNotBlank() == true && uri.rawUserInfo == null
            "http" -> canonicalOrigin(url) in authorizedOrigins()
            else -> false
        }
    }

    companion object {
        fun canonicalOrigin(url: String): String? = runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            if (
                scheme != "http" ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                uri.port !in -1..65535
            ) return null
            val port = if (uri.port >= 0) uri.port else 80
            "$scheme://${uri.host.lowercase()}:$port"
        }.getOrNull()
    }
}

object CleartextOriginAuthorizations {
    /** Unit-test seam; production callers always use the persisted store. */
    @Volatile
    internal var testOrigins: MutableStateFlow<Set<String>>? = null

    private val persistentOriginsFlow by lazy {
        createAnyFlow(
            key = "cleartext_origins",
            default = { emptySet<String>() },
        )
    }

    val originsFlow: MutableStateFlow<Set<String>>
        get() = testOrigins ?: persistentOriginsFlow

    val policy = CleartextOriginPolicy { originsFlow.value }

    fun authorize(url: String): String? = CleartextOriginPolicy.canonicalOrigin(url)?.also { origin ->
        originsFlow.value = originsFlow.value + origin
    }

    fun revoke(origin: String) {
        originsFlow.value = originsFlow.value - origin
    }
}

@Stable
class CleartextOriginInterceptor(
    private val policy: CleartextOriginPolicy = CleartextOriginAuthorizations.policy,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!policy.isAllowed(request.url.toString())) {
            throw IOException("CLEARTEXT_ORIGIN_NOT_AUTHORIZED")
        }
        return chain.proceed(request)
    }
}
