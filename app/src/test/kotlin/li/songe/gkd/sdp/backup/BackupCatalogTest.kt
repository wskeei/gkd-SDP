package li.songe.gkd.sdp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `catalog has six ordered categories with sensitive optional disabled`() {
        assertEquals(
            listOf(
                "settings",
                "subscriptions",
                "self_control_config",
                "self_control_history",
                "upstream_history",
                "sensitive_optional",
            ),
            BackupCatalog.categories.map { it.id },
        )
        assertTrue(BackupCatalog.categories.take(5).all(BackupCategory::defaultEnabled))
        assertFalse(BackupCatalog.categories.last().defaultEnabled)
        assertTrue(BackupCatalog.categories.last().sensitive)
    }

    @Test
    fun `catalog covers every required table exactly once with stable key order`() {
        val expectedTables = setOf(
            "subs_item",
            "subs_config",
            "category_config",
            "app_config",
            "focus_lock",
            "intercept_config",
            "constraint_config",
            "focus_rule",
            "app_group",
            "block_time_rule",
            "app_blocker_lock",
            "url_block_rule",
            "browser_config",
            "url_rule_group",
            "url_time_rule",
            "url_blocker_lock",
            "usage_guard_app_profile",
            "usage_guard_tag",
            "monitored_app",
            "focus_session",
            "usage_guard_record",
            "self_control_attempt",
            "self_control_attempt_event",
            "app_install_log",
            "action_log",
            "activity_log_v2",
            "app_visit_log",
            "snapshot",
            "a11y_event_log",
            "wechat_contact",
        )

        val tableDescriptors = BackupCatalog.categories.flatMap(BackupCategory::tables)
        assertEquals(expectedTables, tableDescriptors.map { it.tableName }.toSet())
        assertEquals(tableDescriptors.size, tableDescriptors.distinctBy { it.tableName }.size)
        assertTrue(tableDescriptors.all { it.primaryKeyColumns.isNotEmpty() })
        assertEquals(
            tableDescriptors.sortedBy { it.objectId }.map { it.objectId },
            BackupCatalog.canonicalObjects(tableDescriptors.reversed()).map { it.objectId },
        )
    }

    @Test
    fun `catalog excludes diagnostics private state and execution material`() {
        val forbidden = setOf(
            "log",
            "crash",
            "cache",
            "shared",
            "private-store",
            "diagnostic-salt.bin",
            "github_cookie.txt",
            "accessibility_guard_session.json",
            "expose.sh",
            "remote-session",
        )

        assertTrue(forbidden.all(BackupCatalog::isExcludedPath))
        assertFalse(BackupCatalog.isExcludedPath("store/store.json"))
    }

    @Test
    fun `legacy importer accepts only known legacy paths`() {
        assertEquals("settings", LegacyBackupImporter.categoryForPath("store/store.json"))
        assertEquals("subscriptions", LegacyBackupImporter.categoryForPath("db.json"))
        assertEquals(
            "subscriptions",
            LegacyBackupImporter.categoryForPath("subscription/123.json"),
        )
        assertNull(LegacyBackupImporter.categoryForPath("../store/store.json"))
        assertNull(LegacyBackupImporter.categoryForPath("commands/expose.sh"))
        assertNull(LegacyBackupImporter.categoryForPath("subscription/run.exe"))
    }

    @Test
    fun `legacy importer converts only known entries into the v2 catalog`() {
        val archive = temporaryFolder.newFile("legacy.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("store/store.json", "{\"enableMatch\":true}")
            output.writeEntry(
                "db.json",
                """{
                    "subsItems":[{
                        "id":1,"ctime":2,"mtime":3,"enable":true,
                        "enableUpdate":false,"order":4,"updateUrl":null
                    }],
                    "subsConfigs":[],"categoryConfigs":[],"appConfigs":[]
                }""".trimIndent(),
            )
            output.writeEntry("subscription/1.json", "{\"id\":1}")
        }

        val result = LegacyBackupImporter.read(archive)

        assertTrue(result is BackupResult.Success)
        val payload = (result as BackupResult.Success<BackupPayload>).value
        assertEquals(listOf("settings", "subscriptions"), payload.manifest.categoryIds)
        assertEquals(
            setOf(
                "settings",
                "table-subs_item",
                "table-subs_config",
                "table-category_config",
                "table-app_config",
            ),
            payload.objects.mapNotNullTo(mutableSetOf()) { objectValue ->
                objectValue.objectId.takeUnless { it.startsWith("subscription-file-") }
            },
        )
        assertEquals(1, payload.objects.count { it.objectId.startsWith("subscription-file-") })
        val subsItem = payload.objects.single { it.objectId == "table-subs_item" }
        assertEquals(1, subsItem.count)
        assertTrue(subsItem.content.decodeToString().contains("enable_update"))
    }

    @Test
    fun `legacy importer rejects archives containing unknown executable entries`() {
        val archive = temporaryFolder.newFile("legacy-unknown.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("store/store.json", "{}")
            output.writeEntry("commands/run.sh", "exit 0")
        }

        val result = LegacyBackupImporter.read(archive)

        assertEquals(
            BackupErrorCode.INVALID_PAYLOAD,
            (result as BackupResult.Failure).code,
        )
    }

    @Test
    fun `payload archive is canonical safe and manifest authenticated by object digests`() {
        val objects = listOf(
            BackupPayloadObject(
                objectId = "table-subs_item",
                categoryId = "subscriptions",
                schema = 1,
                count = 2,
                content = "second".encodeToByteArray(),
            ),
            BackupPayloadObject(
                objectId = "settings",
                categoryId = "settings",
                schema = 1,
                count = 1,
                content = "first".encodeToByteArray(),
            ),
        )
        val first = temporaryFolder.newFile("first.zip")
        val second = temporaryFolder.newFile("second.zip")

        BackupPayloadArchive.build(first, objects)
        BackupPayloadArchive.build(second, objects.reversed())

        assertTrue(first.readBytes().contentEquals(second.readBytes()))
        val parsed = BackupPayloadArchive.read(first) as BackupResult.Success<BackupPayload>
        assertEquals(listOf("settings", "table-subs_item"), parsed.value.objects.map { it.objectId })
        ZipFile(first).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(
                setOf(
                    "manifest.json",
                    "objects/settings.bin",
                    "objects/table-subs_item.bin",
                ),
                names,
            )
            parsed.value.manifest.objects.forEach { item ->
                val bytes = zip.getInputStream(zip.getEntry(item.entryName)).use { it.readBytes() }
                assertEquals(bytes.size.toLong(), item.bytes)
                assertEquals(sha256(bytes), item.sha256)
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.encodeToByteArray())
        closeEntry()
    }
}
