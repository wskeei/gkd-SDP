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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

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
                        text = if (confirmationStep) stringResource(R.string.s_4be37702c8) else stringResource(R.string.s_782087b38b),
                        modifier = modifier,
                    )
                    if (!confirmationStep) {
                        Text(
                            text = stringResource(R.string.s_357a190b67),
                            modifier = modifier,
                        )
                        Text(
                            text = stringResource(R.string.s_e17a2ef166),
                            modifier = modifier,
                        )
                        Text(
                            text = stringResource(R.string.s_89ae336abd),
                            modifier = modifier,
                        )
                        Text(
                            text = stringResource(R.string.s_1fc1afc5c5),
                            modifier = Modifier
                                .clickable(onClick = throttle { confirmationStep = true })
                                .then(modifier),
                        )
                    } else {
                        Text(
                            text = app.getString(R.string.s_7fad32f141),
                            modifier = modifier,
                        )
                        Text(
                            text = app.getString(R.string.s_6605382d7c),
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
                            text = app.getString(R.string.s_b21acfde65),
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
                            text = app.getString(R.string.s_81b17d1c10),
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
                            text = app.getString(R.string.s_22a7b0d512),
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
