package li.songe.gkd.sdp.backup

import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.util.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class AppBackupRepositoryPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validateTableAcceptsValidBackupRows() {
        val descriptor = BackupTableDescriptor("subs_item", listOf("id"))
        val data = BackupTableData(
            tableName = "subs_item",
            columns = listOf("id", "name"),
            rows = listOf(
                listOf(
                    BackupSqlValue(BackupSqlValueType.INTEGER, "1"),
                    BackupSqlValue(BackupSqlValueType.TEXT, "one"),
                ),
            ),
        )

        AppBackupRepository().validateTable(
            descriptor = descriptor,
            data = data,
            declaredCount = 1,
            currentColumns = listOf("id", "name"),
        )
    }

    @Test
    fun validateTableRejectsCountColumnsAndPrimaryKeyProblems() {
        val repository = AppBackupRepository()
        val descriptor = BackupTableDescriptor("subs_item", listOf("id"))
        val valid = tableData()

        assertThrows {
            repository.validateTable(descriptor, valid, declaredCount = 2, listOf("id", "name"))
        }
        assertThrows {
            repository.validateTable(descriptor, valid, declaredCount = 1, listOf("id"))
        }
        assertThrows {
            repository.validateTable(
                descriptor,
                valid.copy(rows = listOf(listOf(BackupSqlValue(BackupSqlValueType.INTEGER, "1")))),
                1,
                listOf("id", "name"),
            )
        }
        assertThrows {
            repository.validateTable(
                descriptor,
                BackupTableData(
                    tableName = "subs_item",
                    columns = listOf("name"),
                    rows = listOf(listOf(BackupSqlValue(BackupSqlValueType.TEXT, "x"))),
                ),
                1,
                listOf("name"),
            )
        }
        assertThrows {
            repository.validateTable(
                descriptor,
                valid.copy(
                    rows = listOf(
                        listOf(
                            BackupSqlValue(BackupSqlValueType.INTEGER, "1"),
                            BackupSqlValue(BackupSqlValueType.TEXT, "one"),
                        ),
                        listOf(
                            BackupSqlValue(BackupSqlValueType.INTEGER, "1"),
                            BackupSqlValue(BackupSqlValueType.TEXT, "duplicate"),
                        ),
                    ),
                ),
                2,
                listOf("id", "name"),
            )
        }
    }

    @Test
    fun validateTableRejectsInvalidSqlValues() {
        val repository = AppBackupRepository()
        assertThrows {
            repository.validateTable(
                BackupTableDescriptor("subs_item", listOf("id")),
                tableData(
                    id = BackupSqlValue(BackupSqlValueType.NULL, "not-null"),
                ),
                1,
                listOf("id", "name"),
            )
        }
        assertThrows {
            repository.validateTable(
                BackupTableDescriptor("subs_item", listOf("id")),
                tableData(
                    id = BackupSqlValue(BackupSqlValueType.TEXT, null),
                ),
                1,
                listOf("id", "name"),
            )
        }
        assertThrows {
            repository.validateTable(
                BackupTableDescriptor("subs_item", listOf("id")),
                tableData(id = BackupSqlValue(BackupSqlValueType.INTEGER, "not-a-number")),
                1,
                listOf("id", "name"),
            )
        }
        assertThrows {
            repository.validateTable(
                BackupTableDescriptor("subs_item", listOf("id")),
                tableData(id = BackupSqlValue(BackupSqlValueType.REAL, "not-a-number")),
                1,
                listOf("id", "name"),
            )
        }
        assertThrows {
            repository.validateTable(
                BackupTableDescriptor("subs_item", listOf("id")),
                tableData(id = BackupSqlValue(BackupSqlValueType.BLOB, "not-base64!")),
                1,
                listOf("id", "name"),
            )
        }
    }

    @Test
    fun logicalReferencesValidateSubscriptionsAttemptsAndSnapshotFiles() {
        val repository = AppBackupRepository()
        val payload = validDecodedPayload()

        assertTrue(repository.validateLogicalReferences(payload))
        assertFalse(
            repository.validateLogicalReferences(
                payload.copy(
                    tableData = payload.tableData + (
                        "subs_config" to tableData(
                            id = BackupSqlValue(BackupSqlValueType.INTEGER, "99"),
                            columnName = "subs_id",
                        )
                    ),
                ),
            ),
        )
    }

    @Test
    fun pathValidatorsRejectTraversalAndAcceptOnlyStableSnapshotLayout() {
        assertFalse(AppBackupRepository.isSafeRelativeFile(""))
        assertFalse(AppBackupRepository.isSafeRelativeFile("../x"))
        assertFalse(AppBackupRepository.isSafeRelativeFile("a/b/../c"))
        assertFalse(AppBackupRepository.isSafeRelativeFile("a\\b"))
        assertFalse(AppBackupRepository.isSafeRelativeFile("/absolute"))
        assertTrue(AppBackupRepository.isSafeRelativeFile("dir/file.json"))

        assertFalse(AppBackupRepository.isValidSnapshotPath("1"))
        assertFalse(AppBackupRepository.isValidSnapshotPath("1/other.json"))
        assertTrue(AppBackupRepository.isValidSnapshotPath("1/1.json"))
        assertTrue(AppBackupRepository.isValidSnapshotPath("1/1.png"))
        assertFalse(AppBackupRepository.isValidSnapshotPath("../1.json"))
    }

    @Test
    fun exportFilesWritesStableObjectsOnlyForAcceptedFiles() {
        val root = temporaryFolder.newFolder("subscriptions")
        root.resolve("1.json").writeText("one")
        root.resolve("2.json").writeText("two")
        root.resolve("ignored.tmp").writeText("skip")

        val objects = AppBackupRepository().exportFiles(
            root = root,
            categoryId = "subscriptions",
            objectPrefix = "subscription-file",
            accept = { it.endsWith(".json") },
        )

        assertEquals(listOf("1.json", "2.json"), objects.map { it.relativePath() })
        assertEquals(2, objects.size)
        assertEquals("subscriptions", objects.first().categoryId)
        assertTrue(objects.map { it.objectId }.toSet().size == 2)
    }

    @Test
    fun decodeFileVerifiesPathHashAndBase64() {
        val repository = AppBackupRepository()
        val file = BackupFileData(
            relativePath = "1.json",
            contentBase64 = Base64.getEncoder().encodeToString("payload".encodeToByteArray()),
        )
        val objectValue = BackupPayloadObject(
            objectId = "subscription-file-${AppBackupRepository.shortHash("1.json")}",
            categoryId = "subscriptions",
            schema = 1,
            count = 1,
            content = json.encodeToString(file).encodeToByteArray(),
        )

        assertEquals("payload", repository.decodeFileBytes(repository.decodeFile(objectValue) { it == "1.json" }).decodeToString())
        assertThrows {
            repository.decodeFile(objectValue) { false }
        }
    }

    private fun tableData(
        id: BackupSqlValue = BackupSqlValue(BackupSqlValueType.INTEGER, "1"),
        columnName: String = "id",
    ) = BackupTableData(
        tableName = "subs_item",
        columns = listOf(columnName, "name"),
        rows = listOf(
            listOf(
                id,
                BackupSqlValue(BackupSqlValueType.TEXT, "one"),
            ),
        ),
    )

    private fun validDecodedPayload(): AppBackupRepository.DecodedPayload {
        val subscriptionFile = BackupFileData(
            relativePath = "1.json",
            contentBase64 = Base64.getEncoder().encodeToString(
                json.encodeToString(
                    RawSubscription(id = 1L, name = "one", version = 1),
                ).encodeToByteArray(),
            ),
        )
        return AppBackupRepository.DecodedPayload(
            categoryIds = setOf("subscriptions", "self_control_history", "sensitive_optional"),
            tableData = mapOf(
                "subs_item" to BackupTableData(
                    "subs_item",
                    listOf("id"),
                    listOf(listOf(BackupSqlValue(BackupSqlValueType.INTEGER, "1"))),
                ),
                "subs_config" to BackupTableData(
                    "subs_config",
                    listOf("subs_id"),
                    listOf(listOf(BackupSqlValue(BackupSqlValueType.INTEGER, "1"))),
                ),
                "self_control_attempt" to BackupTableData(
                    "self_control_attempt",
                    listOf("event_key"),
                    listOf(listOf(BackupSqlValue(BackupSqlValueType.TEXT, "key"))),
                ),
                "self_control_attempt_event" to BackupTableData(
                    "self_control_attempt_event",
                    listOf("event_key"),
                    listOf(listOf(BackupSqlValue(BackupSqlValueType.TEXT, "key"))),
                ),
                "snapshot" to BackupTableData(
                    "snapshot",
                    listOf("id"),
                    listOf(listOf(BackupSqlValue(BackupSqlValueType.INTEGER, "1"))),
                ),
            ),
            settings = null,
            subscriptionFiles = listOf(subscriptionFile),
            snapshotFiles = listOf(
                BackupFileData(
                    relativePath = "1/1.json",
                    contentBase64 = Base64.getEncoder().encodeToString(ByteArray(0)),
                ),
            ),
        )
    }

    private fun BackupPayloadObject.relativePath(): String? =
        json.decodeFromString<BackupFileData>(content.decodeToString()).relativePath

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected failure")
        } catch (_: Throwable) {
        }
    }
}
