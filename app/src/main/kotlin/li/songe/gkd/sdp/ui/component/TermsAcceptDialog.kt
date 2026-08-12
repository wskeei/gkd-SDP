package li.songe.gkd.sdp.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.throttle
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R


@Composable
fun TermsAcceptDialog() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val modifier = Modifier.fillMaxWidth()
    val usageTitle = stringResource(R.string.terms_usage_title)
    val a11yTitle = stringResource(R.string.terms_a11y_title)
    val usageIntro = stringResource(R.string.terms_usage_intro)
    val userAgreement = stringResource(R.string.terms_user_agreement)
    val termsAnd = stringResource(R.string.terms_and)
    val privacyPolicy = stringResource(R.string.terms_privacy_policy)
    val usageSuffix = stringResource(R.string.terms_usage_suffix)
    val stepDataList = remember {
        arrayOf(
            usageTitle to @Composable {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    modifier = modifier,
                    text = buildAnnotatedString {
                        append(usageIntro)
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL12,
                                linkStyles
                            )
                        ) {
                            append(userAgreement)
                        }
                        append(termsAnd)
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL11,
                                linkStyles
                            )
                        ) {
                            append(privacyPolicy)
                        }
                        append(usageSuffix)
                    },
                )
            },
            a11yTitle to @Composable {
                Text(
                    modifier = modifier,
                    text = li.songe.gkd.sdp.app.getString(R.string.s_37c53d9dc0),
                )
            }
        )
    }
    var step by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = throttle {
                if (step < stepDataList.size - 1) {
                    step++
                } else {
                    mainVm.termsAcceptedFlow.value = true
                }
            }) {
                Text(text = stringResource(R.string.s_d5f0847ff2))
            }
        },
        dismissButton = {
            TextButton(onClick = throttle {
                context.finish()
            }) {
                Text(text = stringResource(R.string.s_befce4eeb3))
            }
        }
    )
}
