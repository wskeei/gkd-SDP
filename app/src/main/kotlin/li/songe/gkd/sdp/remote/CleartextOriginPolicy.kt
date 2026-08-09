package li.songe.gkd.sdp.remote

import li.songe.gkd.sdp.store.createAnyFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URI

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
    val originsFlow by lazy {
        createAnyFlow(
            key = "cleartext_origins",
            default = { emptySet<String>() },
        )
    }

    val policy = CleartextOriginPolicy { originsFlow.value }

    fun authorize(url: String): String? = CleartextOriginPolicy.canonicalOrigin(url)?.also { origin ->
        originsFlow.value = originsFlow.value + origin
    }

    fun revoke(origin: String) {
        originsFlow.value = originsFlow.value - origin
    }
}

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
