package li.songe.gkd.sdp.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.util.buildLogFile
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.saveFileToDownloads
import li.songe.gkd.sdp.util.shareFile
import li.songe.gkd.sdp.util.throttle

@Composable
fun ShareLogDlg(showShareLogDlgFlow: MutableStateFlow<Boolean>) {
    var visible by showShareLogDlgFlow.asMutableState()
    if (visible) {
        var confirmationStep by remember(visible) { mutableStateOf(false) }
        val mainVm = LocalMainViewModel.current
        val context = LocalActivity.current as MainActivity
        Dialog(onDismissRequest = { visible = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (confirmationStep) "确认生成支持包" else "支持包内容与隐私说明",
                        modifier = modifier,
                    )
                    if (!confirmationStep) {
                        Text(
                            text = "将包含：应用版本与系统类别、功能开关、权限能力状态、最多 500 条脱敏诊断事件、脱敏崩溃摘要。",
                            modifier = modifier,
                        )
                        Text(
                            text = "不会包含：数据库、设置原文、订阅、申请理由、网址、截图、界面文字、联系人、Cookie、令牌或文件绝对路径。",
                            modifier = modifier,
                        )
                        Text(
                            text = "预计大小：小于 3 MiB。生成前请再次确认。",
                            modifier = modifier,
                        )
                        Text(
                            text = "继续",
                            modifier = Modifier
                                .clickable(onClick = throttle { confirmationStep = true })
                                .then(modifier),
                        )
                    } else {
                        Text(
                            text = "选择下列操作即确认生成仅含上述白名单内容的支持包。",
                            modifier = modifier,
                        )
                        Text(
                            text = "确认并分享到其他应用",
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    visible = false
                                    mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                        val supportBundle = buildLogFile()
                                        context.shareFile(supportBundle, "分享支持包")
                                    }
                                })
                                .then(modifier),
                        )
                        Text(
                            text = "确认并保存到下载",
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    visible = false
                                    mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                        val supportBundle = buildLogFile()
                                        context.saveFileToDownloads(supportBundle)
                                    }
                                })
                                .then(modifier),
                        )
                        Text(
                            text = "确认并生成链接（需要可访问上传服务）",
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    visible = false
                                    mainVm.uploadOptions.startTask(
                                        getFile = { buildLogFile() },
                                    )
                                })
                                .then(modifier),
                        )
                        Text(
                            text = "返回查看内容",
                            modifier = Modifier
                                .clickable(onClick = throttle { confirmationStep = false })
                                .then(modifier),
                        )
                    }
                }
            }
        }
    }
}
