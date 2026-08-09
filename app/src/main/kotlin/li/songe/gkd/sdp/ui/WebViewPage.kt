package li.songe.gkd.sdp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.remote.WebNavigationDecision
import li.songe.gkd.sdp.remote.WebOriginPolicy
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.client
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.openUri
import li.songe.gkd.sdp.util.throttle
import java.net.URI

@Serializable
data class WebViewRoute(val initUrl: String) : NavKey

@Composable
fun WebViewPage(route: WebViewRoute) {
    val initUrl = route.initUrl
    val mainVm = LocalMainViewModel.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember(initUrl) { mutableStateOf("") }
    var loading by remember(initUrl) { mutableStateOf(true) }

    LaunchedEffect(initUrl) {
        when (WebOriginPolicy.decide(initUrl)) {
            WebNavigationDecision.INTERNAL -> mainVm.handleGkdUri(initUrl.toUri())
            WebNavigationDecision.EXTERNAL -> openUri(initUrl.toUri())
            WebNavigationDecision.BLOCK -> loading = false
            WebNavigationDecision.ALLOW -> Unit
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Scaffold(topBar = {
        PerfTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = { mainVm.popPage() },
                )
            },
            title = {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.iconTextSize())
                } else {
                    Text(
                        text = pageTitle.ifBlank { webView?.title.orEmpty() },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                if (chromeVersion in 1..<MINI_CHROME_VERSION) {
                    PerfIconButton(
                        imageVector = PerfIcon.WarningAmber,
                        onClick = throttle {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "兼容性提示",
                                text = "系统 WebView 版本（$chromeVersion）过低，文档可能无法正常显示。请升级系统 WebView，或使用外部浏览器。",
                            )
                        },
                    )
                }
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(imageVector = PerfIcon.MoreVert, onClick = { expanded = true })
                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        if (!loading) {
                            DropdownMenuItem(
                                text = { Text("刷新页面") },
                                onClick = {
                                    expanded = false
                                    webView?.reload()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("复制链接") },
                            onClick = {
                                expanded = false
                                copyText(webView?.url ?: initUrl)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("外部打开") },
                            onClick = {
                                expanded = false
                                val current = webView?.url ?: initUrl
                                if (WebOriginPolicy.decide(current) != WebNavigationDecision.BLOCK) {
                                    openUri(current.toUri())
                                }
                            },
                        )
                    }
                }
            },
        )
    }) { contentPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldPadding(contentPadding),
            factory = { context ->
                WebView.setWebContentsDebuggingEnabled(META.debuggable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    WebView.startSafeBrowsing(context, null)
                }
                WebView(context).apply {
                    webView = this
                    configureSecureSettings(initUrl)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = GkdWebViewClient(
                        onLoadingChanged = { isLoading -> loading = isLoading },
                        onTitleChanged = { title -> pageTitle = title },
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loading = newProgress < 100
                        }
                    }
                    if (WebOriginPolicy.decide(initUrl) == WebNavigationDecision.ALLOW) {
                        loadUrl(initUrl)
                    } else {
                        loading = false
                    }
                }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSecureSettings(initialUrl: String) {
    settings.apply {
        javaScriptEnabled = WebOriginPolicy.decide(initialUrl) == WebNavigationDecision.ALLOW
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (AndroidTarget.TIRAMISU) setAlgorithmicDarkeningAllowed(false)
    }
}

private const val MINI_CHROME_VERSION = 107
private val chromeVersion by lazy {
    WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
}

private const val DOC_CONFIG_URL =
    "https://registry.npmmirror.com/@gkd-kit/docs/latest/files/_config.json"
private const val MAX_DOCUMENT_BYTES = 4L * 1024L * 1024L

@Serializable
private data class DocConfig(
    val mirrorBaseUrl: String,
    val htmlUrlMap: Map<String, String>,
)

private class GkdWebViewClient(
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onTitleChanged: (String) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onLoadingChanged(true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onTitleChanged(view.title.orEmpty())
        onLoadingChanged(false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return when (WebOriginPolicy.decide(request.url.toString())) {
            WebNavigationDecision.ALLOW -> {
                view.settings.javaScriptEnabled = true
                false
            }
            WebNavigationDecision.INTERNAL -> {
                view.settings.javaScriptEnabled = false
                (view.context as? MainActivity)?.mainVm?.handleGkdUri(request.url)
                true
            }
            WebNavigationDecision.EXTERNAL -> {
                view.settings.javaScriptEnabled = false
                openUri(request.url)
                true
            }
            WebNavigationDecision.BLOCK -> {
                view.settings.javaScriptEnabled = false
                true
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (
            request == null ||
            !request.isForMainFrame ||
            request.method != "GET" ||
            WebOriginPolicy.decide(request.url.toString()) != WebNavigationDecision.ALLOW
        ) return null
        return try {
            runBlocking(Dispatchers.IO) { loadMirroredDocument(request.url) }
        } catch (_: Throwable) {
            LogUtils.d("WebView mirror request failed")
            null
        }
    }

    private suspend fun loadMirroredDocument(uri: Uri): WebResourceResponse? {
        val config = client.get(DOC_CONFIG_URL).body<DocConfig>()
        val path = uri.path?.takeIf(String::isNotEmpty) ?: "/"
        val mappedPath = config.htmlUrlMap[path] ?: return null
        val target = runCatching { URI(config.mirrorBaseUrl).resolve(mappedPath).toString() }
            .getOrNull()
            ?.takeIf(WebOriginPolicy::isAllowedMirror)
            ?: return null
        val response = client.get(target)
        if (!response.status.isSuccess()) return null
        val contentType = response.contentType()
        if (contentType?.withoutParameters() != ContentType.Text.Html) return null
        val content = response.body<ByteArray>()
        if (content.size > MAX_DOCUMENT_BYTES) return null
        return WebResourceResponse("text/html", "UTF-8", content.inputStream()).apply {
            responseHeaders = mapOf(
                "Content-Security-Policy" to "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'",
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
            )
        }
    }
}
