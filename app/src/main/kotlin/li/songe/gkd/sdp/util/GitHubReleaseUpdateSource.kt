package li.songe.gkd.sdp.util

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.net.URI

private const val RELEASE_API_URL = "https://api.github.com/repos/wskeei/gkd-SDP/releases"
private const val RELEASE_REPOSITORY_PATH = "/wskeei/gkd-SDP/releases/download/"
private const val RELEASE_HOST = "github.com"

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    val digest: String? = null,
)

@Serializable
data class NewVersion(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileSize: Long,
    val sha256: String,
    val versionLogs: List<VersionLog> = emptyList(),
    @Transient val releaseTag: String = "",
)

@Serializable
data class VersionLog(
    val name: String,
    val code: Int,
    val desc: String,
)

object GitHubReleaseUpdateSource {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun fetchLatest(httpClient: HttpClient, beta: Boolean): NewVersion? {
        val response = httpClient.get(RELEASE_API_URL) {
            expectSuccess = true
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "GKD-SDP-Updater")
        }
        val releases = parseReleasesJson(response.bodyAsText())
        val release = selectLatest(releases, beta) ?: return null
        val manifestAsset = findManifestAsset(release) ?: return null
        val manifestUrl = requireReleaseAssetUrl(manifestAsset.browserDownloadUrl, release.tagName, manifestAsset.name)
        val manifestResponse = httpClient.get(manifestUrl) {
            expectSuccess = true
            header("Accept", "application/octet-stream")
            header("User-Agent", "GKD-SDP-Updater")
        }
        return parseManifest(manifestResponse.bodyAsText(), release)
    }

    fun parseReleasesJson(raw: String): List<GitHubRelease> =
        json.decodeFromString(raw)

    fun selectLatest(releases: List<GitHubRelease>, beta: Boolean): GitHubRelease? =
        releases
            .asSequence()
            .filter { !it.draft && (beta || !it.prerelease) }
            .filter { it.tagName.toReleaseVersion() != null }
            .maxWithOrNull(Comparator { left, right ->
                left.tagName.toReleaseVersion()!!.compareTo(right.tagName.toReleaseVersion()!!)
            })

    fun findManifestAsset(release: GitHubRelease): GitHubReleaseAsset? =
        release.assets.singleOrNull { it.name == "update.json" }

    fun parseManifest(raw: String, release: GitHubRelease): NewVersion {
        val manifest = json.decodeFromString<NewVersion>(raw)
        require(manifest.versionCode > 0) { "update.json versionCode must be positive" }
        require(manifest.versionName.matches(SEMVER_REGEX)) {
            "update.json versionName is not valid SemVer"
        }
        require(release.tagName == "v${manifest.versionName}") {
            "update.json versionName does not match release tag ${release.tagName}"
        }
        require(manifest.fileSize > 0) { "update.json fileSize must be positive" }
        require(manifest.sha256.matches(SHA256_REGEX)) {
            "update.json sha256 must be 64 hexadecimal characters"
        }

        val apkAsset = release.assets.singleOrNull { asset ->
            asset.name.endsWith(".apk") && asset.browserDownloadUrl == manifest.downloadUrl
        } ?: throw IllegalArgumentException("update.json downloadUrl does not identify one APK asset")
        requireReleaseAssetUrl(manifest.downloadUrl, release.tagName, apkAsset.name)
        require(apkAsset.size == manifest.fileSize) {
            "update.json fileSize does not match GitHub asset metadata"
        }
        apkAsset.digest?.let { digest ->
            val normalizedDigest = digest.removePrefix("sha256:").lowercase()
            require(normalizedDigest == manifest.sha256.lowercase()) {
                "update.json sha256 does not match GitHub asset digest"
            }
        }
        return manifest.copy(releaseTag = release.tagName)
    }

    fun isNewer(version: NewVersion, currentVersionCode: Int): Boolean =
        version.versionCode > currentVersionCode

    fun validateDownloadUrl(url: String, releaseTag: String) {
        val uri = URI(url)
        require(uri.scheme == "https") { "Release APK URL must use HTTPS" }
        require(uri.host == RELEASE_HOST) { "Release APK URL must use github.com" }
        require(releaseTag.isNotBlank() && uri.path.startsWith("$RELEASE_REPOSITORY_PATH$releaseTag/")) {
            "Release APK URL must belong to wskeei/gkd-SDP and its release tag"
        }
        require(uri.path.endsWith(".apk")) { "Release download must be an APK" }
    }

    private fun requireReleaseAssetUrl(url: String?, tagName: String, assetName: String): String {
        require(!url.isNullOrBlank()) { "GitHub release asset has no browser download URL" }
        val uri = URI(url)
        require(uri.scheme == "https") { "Release asset URL must use HTTPS" }
        require(uri.host == RELEASE_HOST) { "Release asset URL must use github.com" }
        val expectedPath = "$RELEASE_REPOSITORY_PATH$tagName/$assetName"
        require(uri.path == expectedPath) {
            "Release asset URL must belong to wskeei/gkd-SDP and its release tag"
        }
        return url
    }

    private data class ReleaseVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: List<PreReleaseIdentifier>,
    ) : Comparable<ReleaseVersion> {
        override fun compareTo(other: ReleaseVersion): Int {
            compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)
                .takeIf { it != 0 }
                ?.let { return it }
            if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
            if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
            for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
                val left = prerelease.getOrNull(index)
                val right = other.prerelease.getOrNull(index)
                if (left == null) return -1
                if (right == null) return 1
                val result = left.compareTo(right)
                if (result != 0) return result
            }
            return 0
        }
    }

    private sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
        data class Numeric(val value: Int) : PreReleaseIdentifier
        data class Text(val value: String) : PreReleaseIdentifier

        override fun compareTo(other: PreReleaseIdentifier): Int = when {
            this is Numeric && other is Numeric -> value.compareTo(other.value)
            this is Numeric && other is Text -> -1
            this is Text && other is Numeric -> 1
            this is Text && other is Text -> value.compareTo(other.value)
            else -> 0
        }
    }

    private fun String.toReleaseVersion(): ReleaseVersion? {
        val match = RELEASE_TAG_REGEX.matchEntire(this) ?: return null
        val identifiers = match.groups[4]?.value?.split('.')?.map { part ->
            part.toIntOrNull()?.let(PreReleaseIdentifier::Numeric)
                ?: PreReleaseIdentifier.Text(part)
        } ?: emptyList()
        return ReleaseVersion(
            major = match.groups[1]!!.value.toInt(),
            minor = match.groups[2]!!.value.toInt(),
            patch = match.groups[3]!!.value.toInt(),
            prerelease = identifiers,
        )
    }

    private val RELEASE_TAG_REGEX =
        Regex("^v([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?$")
    private val SEMVER_REGEX =
        Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-(alpha|beta|rc)\\.[0-9]+)?$")
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
}
