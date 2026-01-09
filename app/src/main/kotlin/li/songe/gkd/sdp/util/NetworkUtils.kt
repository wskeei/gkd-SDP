package li.songe.gkd.sdp.util

import java.net.InetAddress

object NetworkUtils {
    fun isAvailable(): Boolean = try {
        InetAddress.getByName("www.baidu.com") != null
    } catch (_: Throwable) {
        false
    }
}