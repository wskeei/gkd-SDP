package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import java.net.NetworkInterface
import java.net.ServerSocket
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

fun getIpAddressInLocalNetwork(): List<String> {
    val networkInterfaces = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
    } catch (e: Exception) {
        // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
        LogUtils.d("network interface lookup failed", e)
        toast(app.getString(R.string.s_99b5891003, DiagnosticLogger.userMessage(e)))
        return emptyList()
    }
    val localAddresses = networkInterfaces.flatMap {
        it.inetAddresses.asSequence().filter { inetAddress ->
            inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                ?: false) && inetAddress.hostAddress != "127.0.0.1"
        }.map { inetAddress -> inetAddress.hostAddress }
    }
    return localAddresses.toList()
}


fun isPortAvailable(port: Int): Boolean {
    var serverSocket: ServerSocket? = null
    return try {
        serverSocket = ServerSocket(port)
        serverSocket.reuseAddress = true
        true
    } catch (_: Exception) {
        false
    } finally {
        serverSocket?.close()
    }
}
