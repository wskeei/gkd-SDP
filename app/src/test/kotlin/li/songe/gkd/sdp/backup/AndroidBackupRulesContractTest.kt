package li.songe.gkd.sdp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidBackupRulesContractTest {
    @Test
    fun `manifest enables only explicit backup rules`() {
        val application = document("app/src/main/AndroidManifest.xml")
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("true", application.androidAttribute("allowBackup"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.androidAttribute("dataExtractionRules"),
        )
        assertEquals("false", application.androidAttribute("backupInForeground"))
        assertEquals("true", application.androidAttribute("killAfterRestore"))
    }

    @Test
    fun `legacy backup rules include only display preferences`() {
        val root = document("app/src/main/res/xml/backup_rules.xml").documentElement

        assertEquals("full-backup-content", root.tagName)
        assertEquals(expectedIncludes, rules(root, "include"))
        assertTrue(rules(root, "exclude").isEmpty())
    }

    @Test
    fun `cloud backup and device transfer share the same allowlist`() {
        val root = document("app/src/main/res/xml/data_extraction_rules.xml").documentElement
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element

        assertEquals(expectedIncludes, rules(cloud, "include"))
        assertEquals(expectedIncludes, rules(transfer, "include"))
        assertTrue(rules(cloud, "exclude").isEmpty())
        assertTrue(rules(transfer, "exclude").isEmpty())
    }

    @Test
    fun `allowlisted files are produced and restored by the application`() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/store/DisplayPreferenceBackup.kt",
        ).readText()

        expectedIncludes.forEach { (_, path) ->
            assertTrue(source.contains(path.substringAfterLast('/')))
        }
        assertTrue(source.contains("initDisplayPreferenceBackup"))
    }

    private val expectedIncludes = setOf(
        "file" to "store/app_theme.json",
        "file" to "store/display_density.json",
        "file" to "store/language.json",
    )

    private fun rules(parent: Element, tagName: String): Set<Pair<String, String>> {
        val nodes = parent.getElementsByTagName(tagName)
        return buildSet {
            repeat(nodes.length) { index ->
                val element = nodes.item(index) as Element
                add(element.getAttribute("domain") to element.getAttribute("path"))
            }
        }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun document(relativePath: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(sourceFile(relativePath))

    private fun sourceFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }
}
