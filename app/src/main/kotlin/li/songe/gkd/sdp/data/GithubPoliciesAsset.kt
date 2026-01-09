package li.songe.gkd.sdp.data

import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.util.FILE_SHORT_URL

@Serializable
data class GithubPoliciesAsset(
    val id: Int,
    val href: String,
) {
    val shortHref: String
        get() = FILE_SHORT_URL + id
}
