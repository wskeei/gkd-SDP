package li.songe.gkd.sdp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import li.songe.gkd.sdp.remote.WebOriginPolicy

class OpenSchemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (
            intent?.action == Intent.ACTION_VIEW &&
            uri != null &&
            WebOriginPolicy.isAllowedInternalDeepLink(uri.toString())
        ) {
            navToMainActivity()
        } else {
            finish()
        }
    }
}
