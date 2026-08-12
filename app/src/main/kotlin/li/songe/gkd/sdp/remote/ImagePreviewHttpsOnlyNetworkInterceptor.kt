package li.songe.gkd.sdp.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ImagePreviewHttpsOnlyNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val decision = ImagePreviewNetworkPolicy.decideNetwork(request.url.toString())
        if (!decision.isAllowed) {
            throw IOException(decision.errorCode)
        }
        return chain.proceed(request)
    }
}
