package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestExportedComponentContractTest {
    @Test
    fun `exported component allowlist is exact and system components retain permissions`() {
        val document = xml("app/src/main/AndroidManifest.xml")
        val exported = buildSet {
            listOf("activity", "service", "receiver", "provider").forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                repeat(nodes.length) { index ->
                    val element = nodes.item(index) as Element
                    if (element.androidAttribute("exported") == "true") {
                        add(element.androidAttribute("name"))
                    }
                }
            }
        }
        assertEquals(EXPORTED_ALLOWLIST, exported)

        val tileServices = elements(document, "service").filter { element ->
            element.androidAttribute("name") in TILE_SERVICES
        }
        assertEquals(7, tileServices.size)
        assertTrue(tileServices.all {
            it.androidAttribute("permission") == "android.permission.BIND_QUICK_SETTINGS_TILE"
        })
        val shizuku = elements(document, "provider").single {
            it.androidAttribute("name") == "rikka.shizuku.ShizukuProvider"
        }
        assertEquals(
            "android.permission.INTERACT_ACROSS_USERS_FULL",
            shizuku.androidAttribute("permission"),
        )
    }

    @Test
    fun `exported activities validate action uri archive and widget inputs`() {
        val scheme = source("app/src/main/kotlin/li/songe/gkd/sdp/OpenSchemeActivity.kt")
        assertTrue(scheme.contains("Intent.ACTION_VIEW"))
        assertTrue(scheme.contains("isAllowedInternalDeepLink"))

        val file = source("app/src/main/kotlin/li/songe/gkd/sdp/OpenFileActivity.kt")
        assertTrue(file.contains("uri.scheme != \"content\""))
        assertTrue(file.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(file.contains("input.read(magic)"))
        assertTrue(file.contains("ZipUtils.validateArchive"))

        val tile = source("app/src/main/kotlin/li/songe/gkd/sdp/OpenTileActivity.kt")
        assertTrue(tile.contains("QS_TILE_PREFERENCES"))
        assertTrue(tile.contains("BIND_QUICK_SETTINGS_TILE"))
        assertTrue(tile.contains("qsTileComponent?.packageName == packageName"))

        val widgetConfig = source(
            "app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetConfigActivity.kt",
        )
        assertTrue(widgetConfig.contains("ACTION_APPWIDGET_CONFIGURE"))
        assertTrue(widgetConfig.contains("getAppWidgetInfo(appWidgetId)?.provider"))
    }

    @Test
    fun `expose service consumes explicit one time capabilities without raw intent output`() {
        val source = source("app/src/main/kotlin/li/songe/gkd/sdp/service/ExposeService.kt")
        assertTrue(source.contains("intent.component != ComponentName"))
        assertTrue(source.contains("intent.action != ACTION_COMMAND"))
        assertTrue(source.contains("intent.extras?.keySet().orEmpty() != expectedExtras"))
        assertTrue(source.contains("issuer.consume(token, action, channel)"))
        assertTrue(source.contains("restrictToOwner"))
        assertFalse(source.contains("未知调用:"))
        assertFalse(source.contains("LogUtils.d(\"ExposeService::handleIntent\""))
    }

    @Test
    fun `notification and widget pending intents are explicit scoped and uniquely keyed`() {
        val notifications = source("app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt")
        assertTrue(notifications.contains("component = MainActivity::class.componentName"))
        assertTrue(notifications.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertTrue(notifications.contains("pendingIntentReqId.incrementAndGet()"))

        val usageWidget = source(
            "app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt",
        )
        assertTrue(usageWidget.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(usageWidget.contains("appWidgetId"))
        assertTrue(usageWidget.contains("PendingIntent.FLAG_IMMUTABLE"))

        val focusWidget = source(
            "app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt",
        )
        assertTrue(focusWidget.contains("Intent(context, FocusQuickStartWidget::class.java)"))
        assertTrue(focusWidget.contains("isValidWidgetId"))
        assertTrue(focusWidget.contains("ruleId !in selectedRuleIds"))
        assertTrue(focusWidget.contains("appWidgetId,"))
        // Android collection fill-in intents require the sole mutable exception; receiver inputs
        // are restricted to this provider, a live widget id and that widget's selected rule ids.
        assertTrue(focusWidget.contains("FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE"))
    }

    private fun source(relativePath: String): String = sourceFile(relativePath).readText()

    private fun xml(relativePath: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(sourceFile(relativePath))

    private fun elements(document: org.w3c.dom.Document, tag: String): List<Element> {
        val nodes = document.getElementsByTagName(tag)
        return List(nodes.length) { index -> nodes.item(index) as Element }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun sourceFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }

    private companion object {
        val TILE_SERVICES = setOf(
            ".service.GkdTileService",
            ".service.SnapshotTileService",
            ".service.HttpTileService",
            ".service.ButtonTileService",
            ".service.MatchTileService",
            ".service.ActivityTileService",
            ".service.EventTileService",
        )
        val EXPORTED_ALLOWLIST = setOf(
            ".MainActivity",
            ".OpenSchemeActivity",
            ".OpenFileActivity",
            ".OpenTileActivity",
            ".widget.FocusWidgetConfigActivity",
            ".service.ExposeService",
            ".receiver.AppInstallReceiver",
            ".widget.FocusQuickStartWidget",
            ".widget.UsageGuardReviewWidget",
            "rikka.shizuku.ShizukuProvider",
        ) + TILE_SERVICES
    }
}
