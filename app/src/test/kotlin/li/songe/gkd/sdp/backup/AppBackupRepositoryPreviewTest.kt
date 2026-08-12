package li.songe.gkd.sdp.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import li.songe.gkd.sdp.util.json

class AppBackupRepositoryPreviewTest {
    @Test
    fun previewReportsAddedOverwrittenAndDeletedKeysPerCategory() = runBlocking {
        val previous = payload(
            category = "settings",
            ids = listOf("theme", "density"),
        )
        val incoming = payload(
            category = "settings",
            ids = listOf("density", "language"),
        )

        val conflicts = AppBackupRepository().preview(previous, incoming)

        assertEquals(1, conflicts.size)
        assertEquals("settings", conflicts.single().categoryId)
        assertEquals(1, conflicts.single().added)
        assertEquals(1, conflicts.single().overwritten)
        assertEquals(1, conflicts.single().deleted)
    }

    @Test
    fun previewProcessesOnlyCategoriesPresentInIncomingManifest() = runBlocking {
        val previous = payload(
            category = "settings",
            ids = listOf("theme"),
        )
        val incoming = payload(
            category = "subscriptions",
            ids = listOf("subs"),
        )

        val conflicts = AppBackupRepository().preview(previous, incoming)

        assertEquals(listOf("subscriptions"), conflicts.map { it.categoryId })
        assertEquals(1, conflicts.single().added)
    }

    @Test
    fun previewUsesStableKeysForTablesFilesAndSettings() = runBlocking {
        val previous = mixedPayload(
            categoryIds = listOf("settings", "subscriptions", "sensitive_optional"),
            objects = listOf(
                settingsObject(listOf(BackupTextFile("theme.json", "dark"))),
                tableObject(
                    tableName = "subs_item",
                    rows = listOf(
                        listOf(sql(BackupSqlValueType.INTEGER, "1"), sql(BackupSqlValueType.TEXT, "old")),
                    ),
                ),
                fileObject("subscription-file-a", "1.json", "old-subscription"),
            ),
        )
        val incoming = mixedPayload(
            categoryIds = listOf("settings", "subscriptions", "sensitive_optional"),
            objects = listOf(
                settingsObject(
                    listOf(
                        BackupTextFile("theme.json", "dark"),
                        BackupTextFile("language.json", "zh-CN"),
                    ),
                ),
                tableObject(
                    tableName = "subs_item",
                    rows = listOf(
                        listOf(sql(BackupSqlValueType.INTEGER, "1"), sql(BackupSqlValueType.TEXT, "new")),
                        listOf(sql(BackupSqlValueType.INTEGER, "2"), sql(BackupSqlValueType.TEXT, "added")),
                    ),
                ),
                fileObject("subscription-file-a", "1.json", "new-subscription"),
                fileObject("subscription-file-b", "2.json", "added-subscription"),
            ),
        )

        val conflicts = AppBackupRepository().preview(previous, incoming)

        assertEquals(listOf("settings", "subscriptions", "sensitive_optional"), conflicts.map { it.categoryId })
        assertEquals(1, conflicts[0].added)
        assertEquals(1, conflicts[0].overwritten)
        assertEquals(0, conflicts[0].deleted)
        assertEquals(2, conflicts[1].added)
        assertEquals(2, conflicts[1].overwritten)
        assertEquals(0, conflicts[1].deleted)
    }

    private fun payload(
        category: String,
        ids: List<String>,
    ): BackupPayload = BackupPayload(
        manifest = BackupPayloadManifest(
            formatVersion = 2,
            categoryIds = listOf(category),
            objects = emptyList(),
        ),
        objects = ids.map { id ->
            BackupPayloadObject(
                objectId = "settings",
                categoryId = category,
                schema = 1,
                count = 1,
                content = json.encodeToString(
                    BackupSettingsData(
                        files = listOf(BackupTextFile(filename = id, text = "synthetic")),
                    ),
                ).encodeToByteArray(),
            )
        },
    )

    private fun mixedPayload(
        categoryIds: List<String>,
        objects: List<BackupPayloadObject>,
    ): BackupPayload = BackupPayload(
        manifest = BackupPayloadManifest(
            formatVersion = 2,
            categoryIds = categoryIds,
            objects = emptyList(),
        ),
        objects = objects,
    )

    private fun settingsObject(files: List<BackupTextFile>): BackupPayloadObject =
        BackupPayloadObject(
            objectId = "settings",
            categoryId = "settings",
            schema = 1,
            count = files.size,
            content = json.encodeToString(BackupSettingsData(files)).encodeToByteArray(),
        )

    private fun tableObject(
        tableName: String,
        rows: List<List<BackupSqlValue>>,
    ): BackupPayloadObject {
        val columns = when (tableName) {
            "subs_item" -> listOf("id", "name")
            else -> error("unsupported test table")
        }
        return BackupPayloadObject(
            objectId = "table-$tableName",
            categoryId = "subscriptions",
            schema = 1,
            count = rows.size,
            content = json.encodeToString(
                BackupTableData(
                    tableName = tableName,
                    columns = columns,
                    rows = rows,
                ),
            ).encodeToByteArray(),
        )
    }

    private fun fileObject(
        objectId: String,
        relativePath: String,
        text: String,
    ): BackupPayloadObject = BackupPayloadObject(
        objectId = objectId,
        categoryId = "subscriptions",
        schema = 1,
        count = 1,
        content = json.encodeToString(
            BackupFileData(
                relativePath = relativePath,
                contentBase64 = java.util.Base64.getEncoder().encodeToString(text.encodeToByteArray()),
            ),
        ).encodeToByteArray(),
    )

    private fun sql(type: BackupSqlValueType, value: String? = null): BackupSqlValue =
        BackupSqlValue(type = type, value = value)
}
