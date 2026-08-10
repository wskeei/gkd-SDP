package li.songe.gkd.sdp.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.DeviceInfo
import li.songe.gkd.sdp.data.GkdAction
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.data.selfAppInfo
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.notif.StopServiceReceiver
import li.songe.gkd.sdp.notif.httpNotif
import li.songe.gkd.sdp.remote.RemoteAuthorizationResult
import li.songe.gkd.sdp.remote.RemoteLimitError
import li.songe.gkd.sdp.remote.RemoteListenMode
import li.songe.gkd.sdp.remote.RemotePairResult
import li.songe.gkd.sdp.remote.RemoteRateLimiter
import li.songe.gkd.sdp.remote.RemoteRequestKind
import li.songe.gkd.sdp.remote.RemoteRevocationReason
import li.songe.gkd.sdp.remote.RemoteScope
import li.songe.gkd.sdp.remote.RemoteSessionPolicy
import li.songe.gkd.sdp.remote.RemoteSessionSnapshot
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.DefaultSimpleLifeImpl
import li.songe.gkd.sdp.util.LOCAL_HTTP_SUBS_ID
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.OnSimpleLife
import li.songe.gkd.sdp.util.SnapshotExt
import li.songe.gkd.sdp.util.SnapshotExt.getMinSnapshot
import li.songe.gkd.sdp.util.deleteSubscription
import li.songe.gkd.sdp.util.getIpAddressInLocalNetwork
import li.songe.gkd.sdp.util.isPortAvailable
import li.songe.gkd.sdp.util.keepNullJson
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.startForegroundServiceByClass
import li.songe.gkd.sdp.util.stopServiceByClass
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.updateSubscription
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import li.songe.gkd.sdp.R

class HttpService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    private val httpServerPortFlow = storeFlow.mapState(scope) { store -> store.httpServerPort }
    private var lastNotificationText: String? = null

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("HTTP服务")
        StopServiceReceiver.autoRegister()
        onCreated {
            registerScreenLockReceiver()
            scope.launchTry(Dispatchers.IO) {
                combine(httpServerPortFlow, listenModeFlow) { port, mode -> port to mode }
                    .collect { (port, mode) -> restartServer(port, mode) }
            }
            scope.launchTry(Dispatchers.IO) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val state = remoteSessionPolicy.snapshot()
                    if (
                        state.mode == RemoteListenMode.LAN &&
                        state.accessExpiresAtMillis?.let { now >= it } == true
                    ) {
                        remoteSessionPolicy.revoke(RemoteRevocationReason.EXPIRED)
                        remoteSessionStateFlow.value = remoteSessionPolicy.snapshot()
                        listenModeFlow.value = RemoteListenMode.LOCAL_ONLY
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_a0ec31af81))
                    } else {
                        notifyRemoteState(state, now)
                    }
                    delay(1_000)
                }
            }
        }
        onDestroyed {
            unregisterScreenLockReceiver()
            remoteSessionPolicy.revoke(RemoteRevocationReason.SERVICE_STOPPED)
            remoteSessionStateFlow.value = remoteSessionPolicy.snapshot()
            rateLimiter.clear()
            if (storeFlow.value.autoClearMemorySubs) {
                appScope.launchTry(Dispatchers.IO) {
                    deleteSubscription(LOCAL_HTTP_SUBS_ID)
                }
            }
            httpServerFlow.value?.stop()
            httpServerFlow.value = null
            listenModeFlow.value = RemoteListenMode.LOCAL_ONLY
        }
    }

    private suspend fun restartServer(port: Int, mode: RemoteListenMode) {
        val wasRunning = httpServerFlow.value != null
        httpServerFlow.value?.stop()
        httpServerFlow.value = null
        remoteSessionPolicy.revoke(RemoteRevocationReason.REPLACED)
        rateLimiter.clear()
        if (!isPortAvailable(port)) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_bba5c9298c, (port).toString()))
            stopSelf()
            return
        }
        localNetworkIpsFlow.value = if (mode == RemoteListenMode.LAN) {
            getIpAddressInLocalNetwork()
        } else {
            emptyList()
        }
        remoteSessionStateFlow.value = remoteSessionPolicy.start(mode, System.currentTimeMillis())
        httpServerFlow.value = try {
            scope.createServer(port, mode.host).apply { start() }
        } catch (error: Exception) {
            LogUtils.d("HTTP service start failed")
            toast(li.songe.gkd.sdp.app.getString(R.string.s_6340271cde))
            null
        }
        if (httpServerFlow.value == null) {
            stopSelf()
        } else {
            notifyRemoteState(remoteSessionStateFlow.value, System.currentTimeMillis())
            if (wasRunning) toast(li.songe.gkd.sdp.app.getString(R.string.s_6e75461ed4))
        }
    }

    private fun notifyRemoteState(state: RemoteSessionSnapshot, nowMillis: Long) {
        val text = if (state.mode == RemoteListenMode.LOCAL_ONLY) {
            "仅本机访问"
        } else {
            val minutes = state.accessExpiresAtMillis
                ?.let { ((it - nowMillis).coerceAtLeast(0) + 59_999) / 60_000 }
                ?: 0
            "局域网会话剩余 $minutes 分钟"
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        with(httpNotif.copy(text = text)) { notifyService() }
    }

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            remoteSessionPolicy.revoke(RemoteRevocationReason.DEVICE_LOCKED)
            remoteSessionStateFlow.value = remoteSessionPolicy.snapshot()
            if (listenModeFlow.value == RemoteListenMode.LAN) {
                listenModeFlow.value = RemoteListenMode.LOCAL_ONLY
            }
        }
    }

    private var screenLockReceiverRegistered = false

    private fun registerScreenLockReceiver() {
        ContextCompat.registerReceiver(
            this,
            screenLockReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenLockReceiverRegistered = true
    }

    private fun unregisterScreenLockReceiver() {
        if (screenLockReceiverRegistered) {
            unregisterReceiver(screenLockReceiver)
            screenLockReceiverRegistered = false
        }
    }

    companion object {
        val httpServerFlow = MutableStateFlow<ServerType?>(null)
        val isRunning = MutableStateFlow(false)
        val localNetworkIpsFlow = MutableStateFlow(emptyList<String>())
        val listenModeFlow = MutableStateFlow(RemoteListenMode.LOCAL_ONLY)
        internal val remoteSessionPolicy = RemoteSessionPolicy()
        internal val rateLimiter = RemoteRateLimiter()
        val remoteSessionStateFlow = MutableStateFlow(remoteSessionPolicy.snapshot())

        fun stop() = stopServiceByClass(HttpService::class)

        fun start() {
            listenModeFlow.value = RemoteListenMode.LOCAL_ONLY
            startForegroundServiceByClass(HttpService::class)
        }

        fun startLanSession() {
            listenModeFlow.value = RemoteListenMode.LAN
            startForegroundServiceByClass(HttpService::class)
        }

        fun disconnectLanSession() {
            remoteSessionPolicy.revoke(RemoteRevocationReason.USER_DISCONNECTED)
            remoteSessionStateFlow.value = remoteSessionPolicy.snapshot()
            listenModeFlow.value = RemoteListenMode.LOCAL_ONLY
        }

        fun setRemoteScope(scope: RemoteScope, enabled: Boolean) {
            remoteSessionStateFlow.value = remoteSessionPolicy.setScope(scope, enabled)
        }
    }
}

typealias ServerType = EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>

@Serializable
data class RpcOk(val message: String? = null)

@Serializable
data class ReqId(val id: Long)

@Serializable
data class ServerInfo(
    val device: DeviceInfo = DeviceInfo(),
    val gkdAppInfo: AppInfo = selfAppInfo,
)

@Serializable
private data class PairRequest(val code: String)

@Serializable
private data class PairResponse(
    val token: String,
    val expiresAtMillis: Long,
    val scopes: Set<RemoteScope>,
)

@Serializable
private data class RemoteErrorResponse(
    val code: String,
    val requestId: String,
    val retryAfterSeconds: Int? = null,
)

fun clearHttpSubs() {
    if (HttpService.isRunning.value) return
    appScope.launchTry {
        delay(1_000)
        if (storeFlow.value.autoClearMemorySubs) deleteSubscription(LOCAL_HTTP_SUBS_ID)
    }
}

private val httpSubsItem = SubsItem(
    id = LOCAL_HTTP_SUBS_ID,
    order = -1,
    enableUpdate = false,
)

private val requestCounter = AtomicLong()

private fun CoroutineScope.createServer(port: Int, host: String) = embeddedServer(
    factory = CIO,
    port = port,
    host = host,
) {
    install(ContentNegotiation) { json(keepNullJson) }
    install(remoteErrorPlugin())
    routing {
        get("/") { call.respondInspectorAsset("http-inspector/index.html", ContentType.Text.Html) }
        get("/app.js") {
            call.respondInspectorAsset(
                "http-inspector/app.js",
                ContentType.parse("text/javascript"),
            )
        }
        get("/app.css") {
            call.respondInspectorAsset("http-inspector/app.css", ContentType.Text.CSS)
        }
        route("/api") {
            post("/session/pair") {
                if (!call.validateBodySize()) return@post
                val rate = HttpService.run {
                    rateLimiter.check(
                        "pair:${call.request.origin.remoteHost}",
                        RemoteRequestKind.DEFAULT,
                        System.currentTimeMillis(),
                    )
                }
                if (!rate.allowed) {
                    call.respondRemoteError(
                        code = "RATE_LIMITED",
                        status = HttpStatusCode.TooManyRequests,
                        retryAfterSeconds = rate.retryAfterSeconds,
                    )
                    return@post
                }
                val result = HttpService.run {
                    remoteSessionPolicy.pair(
                        code = call.receive<PairRequest>().code,
                        clientIp = call.request.origin.remoteHost,
                        userAgent = call.request.header(HttpHeaders.UserAgent).orEmpty(),
                        origin = call.request.header(HttpHeaders.Origin).orEmpty(),
                        nowMillis = System.currentTimeMillis(),
                    )
                }
                when (result) {
                    is RemotePairResult.Failure -> call.respondRemoteError(
                        code = "PAIR_${result.error.name}",
                        status = HttpStatusCode.Unauthorized,
                    )
                    is RemotePairResult.Success -> {
                        val state = HttpService.run { remoteSessionPolicy.snapshot() }
                        HttpService.remoteSessionStateFlow.value = state
                        call.request.header(HttpHeaders.Origin)?.let { call.applyExactCors(it) }
                        call.respondJsonBounded(
                            PairResponse(result.token, result.expiresAtMillis, state.enabledScopes),
                        )
                    }
                }
            }
            post("/getServerInfo") {
                if (!call.requireRemote(RemoteScope.SERVER_INFO)) return@post
                call.respondJsonBounded(ServerInfo())
            }
            post("/getSnapshot") {
                if (!call.requireRemote(RemoteScope.VIEW_SNAPSHOT)) return@post
                call.respondBoundedFile(SnapshotExt.snapshotFile(call.receive<ReqId>().id))
            }
            post("/getScreenshot") {
                if (!call.requireRemote(RemoteScope.VIEW_SNAPSHOT)) return@post
                call.respondBoundedFile(SnapshotExt.screenshotFile(call.receive<ReqId>().id))
            }
            post("/captureSnapshot") {
                if (!call.requireRemote(RemoteScope.CAPTURE_SNAPSHOT, RemoteRequestKind.CAPTURE)) {
                    return@post
                }
                call.respondJsonBounded(SnapshotExt.captureSnapshot())
            }
            post("/getSnapshots") {
                if (!call.requireRemote(RemoteScope.SNAPSHOT_LIST)) return@post
                val snapshots = DbSet.snapshotDao.query().first().mapNotNull { snapshot ->
                    runCatching { getMinSnapshot(snapshot.id) }.getOrNull()
                }
                call.respondJsonBounded(snapshots)
            }
            post("/deleteSnapshot") {
                if (!call.requireRemote(RemoteScope.DELETE_SNAPSHOT)) return@post
                val id = call.receive<ReqId>().id
                val snapshot = DbSet.snapshotDao.query().first().find { it.id == id }
                if (snapshot == null) {
                    call.respondRemoteError("SNAPSHOT_NOT_FOUND", HttpStatusCode.NotFound)
                } else {
                    SnapshotExt.deleteSnapshot(snapshot)
                    call.respondJsonBounded(RpcOk("快照删除成功"))
                }
            }
            post("/updateSubscription") {
                if (!call.requireRemote(RemoteScope.UPDATE_SUBSCRIPTION)) return@post
                val subscription = RawSubscription.parse(call.receiveText(), json5 = false).copy(
                    id = LOCAL_HTTP_SUBS_ID,
                    name = "内存订阅",
                    version = 0,
                    author = "@gkd-kit/inspect",
                )
                val current = subsItemsFlow.value.find { item -> item.id == httpSubsItem.id }
                    ?: httpSubsItem
                updateSubscription(subscription, current)
                call.respondJsonBounded(RpcOk())
            }
            post("/execSelector") {
                if (!call.requireRemote(RemoteScope.EXEC_SELECTOR, RemoteRequestKind.EXEC)) {
                    return@post
                }
                call.respondJsonBounded(A11yRuleEngine.execAction(call.receive<GkdAction>()))
            }
        }
    }
}

private suspend fun ApplicationCall.requireRemote(
    scope: RemoteScope,
    kind: RemoteRequestKind = RemoteRequestKind.DEFAULT,
): Boolean {
    if (!validateBodySize()) return false
    val clientIp = request.origin.remoteHost
    val rate = HttpService.run { rateLimiter.check(clientIp, kind, System.currentTimeMillis()) }
    if (!rate.allowed) {
        respondRemoteError(
            code = "RATE_LIMITED",
            status = HttpStatusCode.TooManyRequests,
            retryAfterSeconds = rate.retryAfterSeconds,
        )
        return false
    }
    val authorization = request.header(HttpHeaders.Authorization).orEmpty()
    val token = authorization.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.takeIf { it.isNotBlank() }
    if (token == null) {
        respondRemoteError("AUTH_REQUIRED", HttpStatusCode.Unauthorized)
        return false
    }
    val origin = request.header(HttpHeaders.Origin)
    val result = HttpService.run {
        remoteSessionPolicy.authorize(
            token = token,
            clientIp = clientIp,
            userAgent = request.header(HttpHeaders.UserAgent).orEmpty(),
            origin = origin,
            requiredScope = scope,
            nowMillis = System.currentTimeMillis(),
        )
    }
    if (result is RemoteAuthorizationResult.Denied) {
        HttpService.remoteSessionStateFlow.value = HttpService.run { remoteSessionPolicy.snapshot() }
        respondRemoteError("AUTH_${result.error.name}", HttpStatusCode.Forbidden)
        return false
    }
    applyExactCors(requireNotNull(origin))
    return true
}

private suspend fun ApplicationCall.validateBodySize(): Boolean {
    val length = request.contentLength()
    val result = if (length == null) {
        li.songe.gkd.sdp.remote.RemoteLimitResult(
            allowed = false,
            error = RemoteLimitError.REQUEST_TOO_LARGE,
        )
    } else {
        HttpService.run { rateLimiter.validateRequestBytes(length) }
    }
    if (!result.allowed) {
        respondRemoteError("REQUEST_TOO_LARGE", HttpStatusCode.PayloadTooLarge)
        return false
    }
    return true
}

private fun ApplicationCall.applyExactCors(origin: String) {
    response.header(HttpHeaders.AccessControlAllowOrigin, origin)
    response.header(HttpHeaders.Vary, HttpHeaders.Origin)
    response.header(HttpHeaders.AccessControlAllowMethods, "POST")
    response.header(HttpHeaders.AccessControlAllowHeaders, HttpHeaders.Authorization)
    response.header(HttpHeaders.AccessControlExposeHeaders, HttpHeaders.RetryAfter)
}

private suspend inline fun <reified T> ApplicationCall.respondJsonBounded(value: T) {
    val body = keepNullJson.encodeToString(value)
    val bytes = body.encodeToByteArray()
    if (bytes.size > RemoteRateLimiter.MAX_RESPONSE_BYTES) {
        respondRemoteError("RESPONSE_TOO_LARGE", HttpStatusCode.PayloadTooLarge)
    } else {
        respondText(body, ContentType.Application.Json)
    }
}

private suspend fun ApplicationCall.respondBoundedFile(file: File) {
    if (!file.isFile) {
        respondRemoteError("SNAPSHOT_NOT_FOUND", HttpStatusCode.NotFound)
    } else if (file.length() > RemoteRateLimiter.MAX_RESPONSE_BYTES) {
        respondRemoteError("RESPONSE_TOO_LARGE", HttpStatusCode.PayloadTooLarge)
    } else {
        respondFile(file)
    }
}

private suspend fun ApplicationCall.respondInspectorAsset(path: String, type: ContentType) {
    val bytes = app.assets.open(path).use { it.readBytes() }
    response.header(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'",
    )
    response.header("X-Content-Type-Options", "nosniff")
    response.header(HttpHeaders.CacheControl, "no-store")
    respondBytes(bytes, type)
}

private suspend fun ApplicationCall.respondRemoteError(
    code: String,
    status: HttpStatusCode,
    retryAfterSeconds: Int? = null,
) {
    if (retryAfterSeconds != null) response.header(HttpHeaders.RetryAfter, retryAfterSeconds)
    val requestId = requestCounter.incrementAndGet().toString(16).padStart(12, '0')
    val body = keepNullJson.encodeToString(
        RemoteErrorResponse(code, requestId, retryAfterSeconds),
    )
    respondText(body, ContentType.Application.Json, status)
}

private fun remoteErrorPlugin() = createApplicationPlugin(name = "RemoteErrorPlugin") {
    on(CallFailed) { call, _ ->
        LogUtils.d("HTTP request failed")
        call.respondRemoteError("REQUEST_FAILED", HttpStatusCode.BadRequest)
    }
}
