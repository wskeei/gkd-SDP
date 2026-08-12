package li.songe.gkd.sdp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.RotatingLoadingIcon
import li.songe.gkd.sdp.ui.component.SettingItem
import li.songe.gkd.sdp.ui.component.TextListDialog
import li.songe.gkd.sdp.ui.component.TextMenu
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.LocalDarkTheme
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.ISSUES_URL
import li.songe.gkd.sdp.util.PLAY_STORE_URL
import li.songe.gkd.sdp.util.REPOSITORY_URL
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.UpdateChannelOption
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.format
import li.songe.gkd.sdp.util.getShareApkFile
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.openUri
import li.songe.gkd.sdp.util.saveFileToDownloads
import li.songe.gkd.sdp.util.shareFile
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource

@Serializable
data object AboutRoute : NavKey

@Composable
fun AboutPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val store by storeFlow.collectAsStateWithLifecycle()

    var showInfoDlg by vm.showInfoDlgFlow.asMutableState()
    if (showInfoDlg) {
        AlertDialog(
            onDismissRequest = { showInfoDlg = false },
            title = { Text(text = stringResource(R.string.s_11740cb0a5)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_25d3a49e6c))
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = stringResource(R.string.s_586a91601e))
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = stringResource(R.string.s_ebb9cdd84c))
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = stringResource(R.string.s_4c28d5df90))
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e2ecfd6487))
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInfoDlg = false
                }) {
                    Text(text = stringResource(R.string.s_6c14bd7f6f))
                }
            },
        )
    }
    var showShareAppDlg by vm.showShareAppDlgFlow.asMutableState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = {
                            mainVm.popPage()
                        },
                    )
                },
                title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_bed172efc9)) },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Share,
                        onClick = {
                            showShareAppDlg = true
                        },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLogoIcon(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast(li.songe.gkd.sdp.app.getString(R.string.s_e60faf5b29)) }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { showInfoDlg = true })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = META.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            SettingItem(
                imageVector = null,
                title = stringResource(R.string.s_177a269ee4),
                onClick = {
                    mainVm.openUrl(REPOSITORY_URL)
                },
            )
            if (META.isGkdChannel) {
                SettingItem(
                    imageVector = null,
                    title = stringResource(R.string.s_960d7b0923),
                    onClick = {
                        mainVm.navigateWebPage(ShortUrlSet.URL10)
                    },
                )
            }
            SettingItem(
                imageVector = null,
                title = stringResource(R.string.s_104a39b093),
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL12)
                },
            )
            SettingItem(
                imageVector = null,
                title = stringResource(R.string.s_8c276c1fea),
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL11)
                },
            )

            Text(
                text = stringResource(R.string.s_42a36e9497),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val feedbackThanks = stringResource(R.string.about_feedback_thanks)
            val feedbackScope = stringResource(R.string.about_feedback_scope)
            val feedbackThirdParty = stringResource(R.string.about_feedback_third_party)
            val feedbackConfident = stringResource(R.string.about_feedback_confident)
            val feedbackContinue = stringResource(R.string.about_feedback_continue)
            Column(
                modifier = Modifier
                    .clickable(onClick = throttle(mainVm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_17068dc79c),
                            textContent = {
                                Text(text = buildAnnotatedString {
                                    val highlightStyle = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    append(feedbackThanks)
                                    withStyle(style = highlightStyle) {
                                        append(feedbackScope)
                                    }
                                    append("\n\n")
                                    append(feedbackThirdParty)
                                    withStyle(style = highlightStyle) {
                                        append(feedbackConfident)
                                    }
                                    append(feedbackContinue)
                                })
                            },
                            confirmText = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5),
                            dismissRequest = true,
                        )
                        mainVm.openUrl(ISSUES_URL)
                    }))
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = stringResource(R.string.s_8d263a68b8),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            SettingItem(
                title = stringResource(R.string.s_252fed9478),
                imageVector = PerfIcon.Share,
                onClick = {
                    mainVm.showShareLogDlgFlow.value = true
                }
            )
            if (mainVm.updateStatus != null) {
                Text(
                    text = stringResource(R.string.s_d9db02d07a),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextMenu(
                    title = stringResource(R.string.s_8af0ad9f92),
                    option = UpdateChannelOption.objects.findOption(store.updateChannel)
                ) {
                    if (mainVm.updateStatus.checkUpdatingFlow.value) return@TextMenu
                    if (it.value == UpdateChannelOption.Beta.value) {
                        mainVm.viewModelScope.launchTry {
                            mainVm.dialogFlow.waitResult(
                                title = li.songe.gkd.sdp.app.getString(R.string.s_f5c895b864),
                                text = li.songe.gkd.sdp.app.getString(R.string.s_d63a373932),
                            )
                            storeFlow.update { s -> s.copy(updateChannel = it.value) }
                        }
                    } else {
                        storeFlow.update { s -> s.copy(updateChannel = it.value) }
                    }
                }
                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle {
                                mainVm.updateStatus.checkUpdate(true)
                            }
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.s_a6df38586d),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RotatingLoadingIcon(loading = mainVm.updateStatus.checkUpdatingFlow.collectAsStateWithLifecycle().value)
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    if (showShareAppDlg) {
        TextListDialog(
            onDismiss = { showShareAppDlg = false },
            textList = listOf(
                li.songe.gkd.sdp.app.getString(R.string.share_to_other_apps) to mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        mainVm.dialogFlow.waitResult(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_ba964c4042),
                            textContent = { Text(text = exportPlayTipTemplate()) },
                            confirmText = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5),
                        )
                    }
                    context.shareFile(getShareApkFile(), li.songe.gkd.sdp.app.getString(R.string.share_install_file))
                },
                li.songe.gkd.sdp.app.getString(R.string.save_to_downloads) to mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        mainVm.dialogFlow.waitResult(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_108a9199f2),
                            textContent = { Text(text = exportPlayTipTemplate()) },
                            confirmText = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5),
                        )
                    }
                    context.saveFileToDownloads(getShareApkFile())
                },
                "Google Play" to {
                    mainVm.openUrl(PLAY_STORE_URL)
                },
            )
        )
    }
}

@Composable
private fun exportPlayTipTemplate(): AnnotatedString {
    val tip = stringResource(R.string.about_export_play_tip)
    val downloadLink = stringResource(R.string.about_export_play_download_link)
    val continueText = stringResource(R.string.about_export_play_continue)
    return buildAnnotatedString {
        append(tip)
        withLink(
            LinkAnnotation.Url(
                ShortUrlSet.URL13,
                TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
            )
        ) {
            append(downloadLink)
        }
        append(continueText)
    }
}

@Composable
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalDarkTheme.current
    val colorRid = if (darkTheme) R.color.better_white else R.color.better_black
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_anim_logo)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (isActive) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}
