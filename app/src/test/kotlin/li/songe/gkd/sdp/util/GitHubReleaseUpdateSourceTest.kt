package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdateSourceTest {
    @Test
    fun stableSelectionSkipsDraftsAndPrereleases() {
        val releases = GitHubReleaseUpdateSource.parseReleasesJson(
            """
            [
              {"tag_name":"v2.0.0-beta.2","draft":false,"prerelease":true,"assets":[]},
              {"tag_name":"v2.0.0","draft":true,"prerelease":false,"assets":[]},
              {"tag_name":"v1.9.0","draft":false,"prerelease":false,"assets":[]},
              {"tag_name":"v1.10.0","draft":false,"prerelease":false,"assets":[]}
            ]
            """.trimIndent()
        )

        assertEquals("v1.10.0", GitHubReleaseUpdateSource.selectLatest(releases, beta = false)?.tagName)
    }

    @Test
    fun betaSelectionIncludesStableAndPrereleaseButSkipsDrafts() {
        val releases = listOf(
            release("v2.0.0-beta.1", prerelease = true),
            release("v2.0.0", prerelease = false),
            release("v2.1.0-alpha.1", prerelease = true),
            release("v2.2.0-beta.1", draft = true, prerelease = true),
        )

        assertEquals("v2.1.0-alpha.1", GitHubReleaseUpdateSource.selectLatest(releases, beta = true)?.tagName)
    }

    @Test
    fun invalidOrOverflowingTagsDoNotBreakSelection() {
        val releases = listOf(
            release("v999999999999999999999.0.0"),
            release("v2.0.0-beta.1", prerelease = true),
            release("v2.0.0-foo.1", prerelease = true),
        )

        assertEquals(
            "v2.0.0-beta.1",
            GitHubReleaseUpdateSource.selectLatest(releases, beta = true)?.tagName,
        )
    }

    @Test
    fun manifestParsingValidatesVersionTagDownloadSizeAndDigest() {
        val apkUrl = "https://github.com/wskeei/gkd-SDP/releases/download/v2.0.0-beta.1/gkd-sdp-v2.0.0-beta.1.apk"
        val digest = "a".repeat(64)
        val release = release(
            tagName = "v2.0.0-beta.1",
            assets = listOf(
                GitHubReleaseAsset("update.json", 412, "https://github.com/wskeei/gkd-SDP/releases/download/v2.0.0-beta.1/update.json", null),
                GitHubReleaseAsset("gkd-sdp-v2.0.0-beta.1.apk", 1234, apkUrl, "sha256:$digest"),
            ),
            prerelease = true,
        )
        val manifest = """
            {
              "versionCode": 93,
              "versionName": "2.0.0-beta.1",
              "changelog": "Initial SDP beta",
              "downloadUrl": "$apkUrl",
              "fileSize": 1234,
              "sha256": "$digest",
              "versionLogs": []
            }
        """.trimIndent()

        val parsed = GitHubReleaseUpdateSource.parseManifest(manifest, release)

        assertEquals(93, parsed.versionCode)
        assertEquals("2.0.0-beta.1", parsed.versionName)
        assertEquals(apkUrl, parsed.downloadUrl)
        assertEquals(1234, parsed.fileSize)
        assertEquals(digest, parsed.sha256)
        assertEquals("v2.0.0-beta.1", parsed.releaseTag)
    }

    @Test
    fun draftOrReleaseWithoutManifestIsNotSelectableForUpdate() {
        val draft = release("v2.0.0", draft = true)
        val missingManifest = release("v2.0.1", assets = listOf(apkAsset("v2.0.1")))

        assertNull(GitHubReleaseUpdateSource.selectLatest(listOf(draft), beta = false))
        assertNull(GitHubReleaseUpdateSource.findManifestAsset(missingManifest))
    }

    @Test
    fun currentVersionDoesNotReportAnUpdate() {
        val version = NewVersion(
            versionCode = 93,
            versionName = "2.0.0-beta.1",
            changelog = "",
            downloadUrl = "https://github.com/wskeei/gkd-SDP/releases/download/v2.0.0-beta.1/app.apk",
            fileSize = 1,
            sha256 = "a".repeat(64),
        )

        assertFalse(GitHubReleaseUpdateSource.isNewer(version, currentVersionCode = 93))
        assertTrue(GitHubReleaseUpdateSource.isNewer(version.copy(versionCode = 94), currentVersionCode = 93))
    }

    @Test
    fun malformedApiJsonAndUnexpectedApiObjectAreRejected() {
        assertThrows { GitHubReleaseUpdateSource.parseReleasesJson("not-json") }
        assertThrows { GitHubReleaseUpdateSource.parseReleasesJson("{\"message\":\"API rate limit exceeded\"}") }
    }

    @Test
    fun invalidManifestUrlsAndUnexpectedRepositoryAreRejected() {
        val release = release(
            tagName = "v2.0.0-beta.1",
            assets = listOf(apkAsset("v2.0.0-beta.1")),
            prerelease = true,
        )
        val insecure = manifestJson(
            downloadUrl = "http://github.com/wskeei/gkd-SDP/releases/download/v2.0.0-beta.1/gkd-sdp-v2.0.0-beta.1.apk"
        )
        val wrongRepository = manifestJson(
            downloadUrl = "https://github.com/example/other/releases/download/v2.0.0-beta.1/gkd-sdp-v2.0.0-beta.1.apk"
        )

        assertThrows { GitHubReleaseUpdateSource.parseManifest(insecure, release) }
        assertThrows { GitHubReleaseUpdateSource.parseManifest(wrongRepository, release) }
    }

    private fun release(
        tagName: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GitHubReleaseAsset> = listOf(apkAsset(tagName)),
    ) = GitHubRelease(tagName, draft, prerelease, assets)

    private fun apkAsset(tagName: String): GitHubReleaseAsset {
        val versionName = tagName.removePrefix("v")
        return GitHubReleaseAsset(
            name = "gkd-sdp-v$versionName.apk",
            size = 1234,
            browserDownloadUrl = "https://github.com/wskeei/gkd-SDP/releases/download/$tagName/gkd-sdp-v$versionName.apk",
            digest = null,
        )
    }

    private fun manifestJson(
        downloadUrl: String,
    ) = """
        {
          "versionCode": 93,
          "versionName": "2.0.0-beta.1",
          "changelog": "Initial SDP beta",
          "downloadUrl": "$downloadUrl",
          "fileSize": 1234,
          "sha256": "${"a".repeat(64)}",
          "versionLogs": []
        }
    """.trimIndent()

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: Exception) {
            return
        }
        throw AssertionError("Expected an exception")
    }
}
