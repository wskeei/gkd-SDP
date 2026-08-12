@file:JvmName("ImagePreviewSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.webkit.URLUtil
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.remote.ImagePreviewHttpsOnlyNetworkInterceptor
import li.songe.gkd.sdp.remote.ImagePreviewNetworkPolicy
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.coilCacheDir
import li.songe.gkd.sdp.util.throttle
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import li.songe.gkd.sdp.R

internal val imageLoader by lazy {
    ImageLoader.Builder(app)
        .diskCache {
            DiskCache.Builder()
                .directory(coilCacheDir.toOkioPath())
                .maxSizePercent(0.1)
                .build()
        }
        .components {
            if (AndroidTarget.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = {
                        OkHttpClient.Builder()
                            .connectTimeout(30.seconds.toJavaDuration())
                            .readTimeout(30.seconds.toJavaDuration())
                            .writeTimeout(30.seconds.toJavaDuration())
                            .addNetworkInterceptor(ImagePreviewHttpsOnlyNetworkInterceptor())
                            .build()
                    }
                ))
        }
        .build()
}


@Composable
internal fun ImagePreviewPageSections(
    route: ImagePreviewRoute,
    uiState: ImagePreviewUiState,
    pagerState: PagerState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleBars: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity

    // 路由同时兼容旧的 uri/uris 和新的 items，预览页内部统一按图片项处理。
    val previewItems = uiState.items
    val previewUris = remember(previewItems) { previewItems.map { it.uri } }
    val singleItem = previewItems.singleOrNull()
    val showBars = uiState.showBars

    val controller = remember {
        WindowCompat.getInsetsController(context.window, context.window.decorView)
    }
    DisposableEffect(null) {
        val oldBehavior = controller.systemBarsBehavior
        val oldLight = controller.isAppearanceLightStatusBars
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        onDispose {
            controller.systemBarsBehavior = oldBehavior
            controller.isAppearanceLightStatusBars = oldLight
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    LaunchedEffect(showBars) {
        if (showBars) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // 规则组示例图会连续横滑，但预取并发限制在 2，避免与首图显示请求抢带宽。
    LaunchedEffect(previewUris) {
        if (previewUris.size <= 1) return@LaunchedEffect
        previewUris
            .drop(1)
            .filter {
                ImagePreviewNetworkPolicy.isNetworkUri(it) &&
                    ImagePreviewNetworkPolicy.decideNetwork(it).isAllowed
            }
            .chunked(2)
            .forEach { uriBatch ->
                uriBatch.map { preloadUri ->
                    async {
                        imageLoader.execute(
                            buildPreviewImageRequest(
                                context = context,
                                uri = preloadUri,
                            )
                        )
                    }
                }.awaitAll()
            }
    }

    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
    ) {
        ImagePreviewMedia(
            previewItems = previewItems,
            singleItem = singleItem,
            pagerState = pagerState,
            onToggleBars = onToggleBars,
        )
        ImagePreviewBars(
            route = route,
            previewItems = previewItems,
            singleItem = singleItem,
            pagerState = pagerState,
            showBars = showBars,
            onBack = onBack,
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun ImagePreviewMedia(
    previewItems: List<ImagePreviewItem>,
    singleItem: ImagePreviewItem?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onToggleBars: () -> Unit,
) {
    when {
        singleItem != null -> UriImage(uri = singleItem.uri, onToggleBars = onToggleBars)
        previewItems.isNotEmpty() -> {
            HorizontalPager(modifier = Modifier.fillMaxSize(), state = pagerState) { index ->
                UriImage(uri = previewItems[index].uri, onToggleBars = onToggleBars)
            }
        }
    }
}

@Composable
private fun ImagePreviewBars(
    route: ImagePreviewRoute,
    previewItems: List<ImagePreviewItem>,
    singleItem: ImagePreviewItem?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    showBars: Boolean,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = showBars,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(1f).fillMaxWidth(),
    ) {
        val currentPreviewItem = singleItem ?: previewItems.getOrNull(pagerState.currentPage)
        val currentUri = currentPreviewItem?.uri
        Column {
            PerfTopAppBar(
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)),
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    )
                },
                title = { ImagePreviewTitle(route.title, currentPreviewItem) },
                actions = {
                    if (currentUri != null && URLUtil.isNetworkUrl(currentUri)) {
                        PerfIconButton(
                            imageVector = PerfIcon.OpenInNew,
                            onClick = throttle(fn = { onOpenUrl(currentUri) }),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
            if (previewItems.size > 1) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_7adaf20edf, (pagerState.currentPage + 1).toString(), (previewItems.size).toString()),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewTitle(baseTitle: String?, item: ImagePreviewItem?) {
    val title = baseTitle?.takeIf { it.isNotBlank() }
    val itemTitle = item?.let(::buildPreviewSubtitle)?.takeIf { it.isNotBlank() && it != title }
    when {
        title != null && itemTitle != null -> Column {
            ImagePreviewTitleText(title, MaterialTheme.typography.titleLarge)
            ImagePreviewTitleText(itemTitle, MaterialTheme.typography.titleSmall, alpha = 0.8f)
        }
        title != null -> ImagePreviewTitleText(title, MaterialTheme.typography.titleLarge)
        itemTitle != null -> ImagePreviewTitleText(itemTitle, MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ImagePreviewTitleText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    alpha: Float = 1f,
) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.MiddleEllipsis,
        style = style.copy(color = Color.White.copy(alpha = alpha), fontWeight = if (alpha < 1f) FontWeight.Normal else FontWeight.Medium),
    )
}
