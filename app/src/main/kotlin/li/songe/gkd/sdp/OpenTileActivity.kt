package li.songe.gkd.sdp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.net.toUri
import li.songe.gkd.sdp.remote.WebOriginPolicy
import li.songe.gkd.sdp.util.extraCptName

class OpenTileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qsTileComponent = intent?.extraCptName
        val uriValue = if (
            intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES" &&
            intent?.data == null &&
            qsTileComponent?.packageName == packageName
        ) {
            runCatching {
                val serviceInfo = packageManager.getServiceInfo(
                    requireNotNull(qsTileComponent),
                    PackageManager.GET_META_DATA,
                )
                require(serviceInfo.exported)
                require(serviceInfo.permission == Manifest.permission.BIND_QUICK_SETTINGS_TILE)
                serviceInfo.metaData?.getString("QS_TILE_URI")
            }.getOrNull()
        } else {
            null
        }
        if (uriValue != null && WebOriginPolicy.isAllowedInternalDeepLink(uriValue)) {
            intent.data = uriValue.toUri()
            navToMainActivity()
        } else {
            finish()
        }
    }
}
