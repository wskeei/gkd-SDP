package li.songe.gkd.sdp.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.a11y.sdpRuntimeFeatureCoordinator
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.AutoReenableEnforcer
import li.songe.gkd.sdp.store.replaceNormalStoreTexts
import li.songe.gkd.sdp.store.snapshotNormalStoreTexts
import li.songe.gkd.sdp.store.writeFileAtomically
import li.songe.gkd.sdp.util.initSubsState
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.requirePendingDataRecoveryComplete
import li.songe.gkd.sdp.util.snapshotFolder
import li.songe.gkd.sdp.util.subsFolder
import li.songe.gkd.sdp.util.subsMapFlow
import java.io.File
import java.security.MessageDigest
import java.util.Base64

class AppBackupRepository : BackupExportSource, BackupImportTarget {
    override suspend fun <T> withExclusiveMutation(
        block: suspend () -> T,
        afterCommit: suspend (T) -> Unit,
    ): T =
        BackupDataMutationBarrier.withConsistentDataSnapshot {
            val result = DbSet.withTransaction { block() }
            withContext(NonCancellable) {
                BackupDataMutationBarrier.commitPendingDataReplacements()
            }
            afterCommit(result)
            result
        }

    override suspend fun <T> withRecoveryMutation(block: suspend () -> T): T =
        BackupDataMutationBarrier.withConsistentDataSnapshot {
            val result = DbSet.withTransaction { block() }
            withContext(NonCancellable) {
                BackupDataMutationBarrier.commitPendingDataReplacements()
            }
            result
        }

    override suspend fun collect(categoryIds: Set<String>): BackupPayload {
        requirePendingDataRecoveryComplete()
        return BackupDataMutationBarrier.withConsistentDataSnapshot {
            DbSet.withRawTransaction { database ->
                collectConsistent(categoryIds, database)
            }
        }
    }

    private fun collectConsistent(
        categoryIds: Set<String>,
        database: SupportSQLiteDatabase,
    ): BackupPayload {
        require(categoryIds.isNotEmpty())
        require(categoryIds.all { BackupCatalog.category(it) != null })
        val objects = mutableListOf<BackupPayloadObject>()
        if ("settings" in categoryIds) objects += exportSettings()
        BackupCatalog.categories
            .filter { it.id in categoryIds }
            .flatMap(BackupCategory::tables)
            .forEach { descriptor -> objects += exportTable(database, descriptor) }
        if ("subscriptions" in categoryIds) {
            objects += exportFiles(
                root = subsFolder,
                categoryId = "subscriptions",
                objectPrefix = "subscription-file",
                accept = { relativePath -> SUBSCRIPTION_PATH.matches(relativePath) },
            )
        }
        if ("sensitive_optional" in categoryIds) {
            objects += exportFiles(
                root = snapshotFolder,
                categoryId = "sensitive_optional",
                objectPrefix = "snapshot-file",
                accept = ::isValidSnapshotPath,
            )
        }
        return BackupPayloadArchive.createPayload(objects)
    }

    override suspend fun validateReferences(payload: BackupPayload): Boolean = runCatching {
        DbSet.withRawTransaction { database ->
            val decoded = decodeAndValidate(database, payload)
            validateLogicalReferences(decoded)
        }
    }.getOrDefault(false)

    override suspend fun capture(categoryIds: Set<String>): BackupPayload = collect(categoryIds)

    override suspend fun preview(
        previous: BackupPayload,
        incoming: BackupPayload,
    ): List<BackupConflictPreview> {
        val previousKeys = keysByCategory(previous)
        val incomingKeys = keysByCategory(incoming)
        return incoming.manifest.categoryIds.map { categoryId ->
            val old = previousKeys[categoryId].orEmpty()
            val new = incomingKeys[categoryId].orEmpty()
            BackupConflictPreview(
                categoryId = categoryId,
                added = (new - old).size,
                overwritten = (new intersect old).size,
                deleted = (old - new).size,
            )
        }
    }

    override suspend fun replaceIncludedCategories(payload: BackupPayload) {
        val decoded = DbSet.withRawTransaction { database ->
            decodeAndValidate(database, payload).also { value ->
                replaceTables(database, value)
            }
        }
        replaceFilesAndStores(decoded)
    }

    override suspend fun restore(previous: BackupPayload) {
        replaceIncludedCategories(previous)
    }

    override suspend fun reconcileRuntime() {
        subsMapFlow.value = emptyMap()
        initSubsState()
        UsageGuardEngine.reconcileAfterConfigurationImport()
        sdpRuntimeFeatureCoordinator.reconcileCurrentApp("backup-import-completed")
        AutoReenableEnforcer.start()
    }

    private fun exportSettings(): BackupPayloadObject {
        val files = snapshotNormalStoreTexts()
            .filterKeys { filename -> !BackupCatalog.isExcludedPath(filename) }
            .toSortedMap()
            .map { (filename, text) -> BackupTextFile(filename, text) }
        return BackupPayloadObject(
            objectId = SETTINGS_OBJECT_ID,
            categoryId = "settings",
            schema = 1,
            count = files.size,
            content = json.encodeToString(BackupSettingsData(files)).encodeToByteArray(),
        )
    }

    private fun exportTable(
        database: SupportSQLiteDatabase,
        descriptor: BackupTableDescriptor,
    ): BackupPayloadObject {
        val orderBy = descriptor.primaryKeyColumns.joinToString(",") { quoteIdentifier(it) }
        val tableData = database.query(
            "SELECT * FROM ${quoteIdentifier(descriptor.tableName)} ORDER BY $orderBy",
        ).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(columns.indices.map { columnIndex -> cursor.readSqlValue(columnIndex) })
                }
            }
            BackupTableData(descriptor.tableName, columns, rows)
        }
        return BackupPayloadObject(
            objectId = descriptor.objectId,
            categoryId = requireNotNull(categoryForTable(descriptor.tableName)).id,
            schema = 1,
            count = tableData.rows.size,
            content = json.encodeToString(tableData).encodeToByteArray(),
        )
    }

    private fun exportFiles(
        root: File,
        categoryId: String,
        objectPrefix: String,
        accept: (String) -> Boolean,
    ): List<BackupPayloadObject> {
        val canonicalRoot = root.canonicalFile
        return root.walkTopDown()
            .filter(File::isFile)
            .map { file ->
                val canonicalFile = file.canonicalFile
                require(canonicalFile.path.startsWith(canonicalRoot.path + File.separator))
                canonicalFile.relativeTo(canonicalRoot).invariantSeparatorsPath to canonicalFile
            }
            .filter { (relativePath) -> accept(relativePath) }
            .sortedBy { (relativePath) -> relativePath }
            .map { (relativePath, file) ->
                val data = BackupFileData(
                    relativePath = relativePath,
                    contentBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
                )
                BackupPayloadObject(
                    objectId = "$objectPrefix-${shortHash(relativePath)}",
                    categoryId = categoryId,
                    schema = 1,
                    count = 1,
                    content = json.encodeToString(data).encodeToByteArray(),
                )
            }
            .toList()
    }

    private fun decodeAndValidate(
        database: SupportSQLiteDatabase,
        payload: BackupPayload,
    ): DecodedPayload {
        val selectedCategories = payload.manifest.categoryIds.toSet()
        require(selectedCategories.isNotEmpty())
        require(payload.objects.all { it.categoryId in selectedCategories })
        val tableData = linkedMapOf<String, BackupTableData>()
        var settings: BackupSettingsData? = null
        val subscriptionFiles = mutableListOf<BackupFileData>()
        val snapshotFiles = mutableListOf<BackupFileData>()

        payload.objects.forEach { objectValue ->
            when {
                objectValue.objectId == SETTINGS_OBJECT_ID -> {
                    require(objectValue.categoryId == "settings" && settings == null)
                    settings = json.decodeFromString(objectValue.content.decodeToString())
                    require(settings!!.files.size == objectValue.count)
                }
                objectValue.objectId.startsWith("table-") -> {
                    val data = json.decodeFromString<BackupTableData>(
                        objectValue.content.decodeToString(),
                    )
                    val descriptor = descriptorForTable(data.tableName)
                    require(descriptor != null && objectValue.objectId == descriptor.objectId)
                    require(objectValue.categoryId == categoryForTable(data.tableName)?.id)
                    validateTable(database, descriptor, data, objectValue.count)
                    require(tableData.put(data.tableName, data) == null)
                }
                objectValue.objectId.startsWith("subscription-file-") -> {
                    require(objectValue.categoryId == "subscriptions" && objectValue.count == 1)
                    subscriptionFiles += decodeFile(objectValue, SUBSCRIPTION_PATH::matches)
                }
                objectValue.objectId.startsWith("snapshot-file-") -> {
                    require(objectValue.categoryId == "sensitive_optional" && objectValue.count == 1)
                    snapshotFiles += decodeFile(objectValue, ::isValidSnapshotPath)
                }
                else -> error("unknown_backup_object")
            }
        }
        selectedCategories.forEach { categoryId ->
            val category = requireNotNull(BackupCatalog.category(categoryId))
            require(category.tables.all { it.tableName in tableData })
            if (categoryId == "settings") require(settings != null)
        }
        require(tableData.keys.all { categoryForTable(it)?.id in selectedCategories })
        require(settings?.files.orEmpty().map(BackupTextFile::filename).let { it.size == it.distinct().size })
        settings?.files.orEmpty().forEach { file ->
            require(isSafeRelativeFile(file.filename) && !BackupCatalog.isExcludedPath(file.filename))
        }
        require(subscriptionFiles.map(BackupFileData::relativePath).distinct().size == subscriptionFiles.size)
        require(snapshotFiles.map(BackupFileData::relativePath).distinct().size == snapshotFiles.size)
        return DecodedPayload(
            categoryIds = selectedCategories,
            tableData = tableData,
            settings = settings,
            subscriptionFiles = subscriptionFiles,
            snapshotFiles = snapshotFiles,
        )
    }

    private fun validateTable(
        database: SupportSQLiteDatabase,
        descriptor: BackupTableDescriptor,
        data: BackupTableData,
        declaredCount: Int,
    ) {
        require(data.rows.size == declaredCount)
        val currentColumns = tableColumns(database, data.tableName)
        require(data.columns == currentColumns)
        require(data.rows.all { it.size == data.columns.size })
        val primaryKeyIndices = descriptor.primaryKeyColumns.map(data.columns::indexOf)
        require(primaryKeyIndices.all { it >= 0 })
        val keys = data.rows.map { row ->
            primaryKeyIndices.joinToString("|") { index -> row[index].stableKey() }
        }
        require(keys.size == keys.distinct().size)
        data.rows.flatten().forEach { value ->
            require((value.type == BackupSqlValueType.NULL) == (value.value == null))
            when (value.type) {
                BackupSqlValueType.NULL, BackupSqlValueType.TEXT -> Unit
                BackupSqlValueType.INTEGER -> value.value!!.toLong()
                BackupSqlValueType.REAL -> value.value!!.toDouble()
                BackupSqlValueType.BLOB -> Base64.getDecoder().decode(value.value)
            }
        }
    }

    private fun validateLogicalReferences(decoded: DecodedPayload): Boolean {
        fun values(table: String, column: String): Set<String> {
            val data = decoded.tableData[table] ?: return emptySet()
            val index = data.columns.indexOf(column)
            if (index < 0) return emptySet()
            return data.rows.mapNotNullTo(mutableSetOf()) { row -> row[index].value }
        }
        val subscriptionIds = values("subs_item", "id")
        for (child in listOf("subs_config", "category_config", "app_config")) {
            if (!values(child, "subs_id").all(subscriptionIds::contains)) return false
        }
        val attemptKeys = values("self_control_attempt", "event_key")
        if (!values("self_control_attempt_event", "event_key").all(attemptKeys::contains)) {
            return false
        }
        decoded.subscriptionFiles.forEach { file ->
            val id = file.relativePath.removeSuffix(".json").toLong()
            if (id.toString() !in subscriptionIds) return false
            val raw = json.decodeFromString<RawSubscription>(decodeFileBytes(file).decodeToString())
            if (raw.id != id) return false
        }
        val snapshotIds = values("snapshot", "id")
        if (decoded.snapshotFiles.any { file ->
                file.relativePath.substringBefore('/').let { it !in snapshotIds }
            }
        ) return false
        return true
    }

    private fun replaceTables(
        database: SupportSQLiteDatabase,
        decoded: DecodedPayload,
    ) {
        BackupCatalog.categories.filter { it.id in decoded.categoryIds }
            .flatMap(BackupCategory::tables)
            .forEach { descriptor ->
                database.execSQL("DELETE FROM ${quoteIdentifier(descriptor.tableName)}")
            }
        BackupCatalog.categories.filter { it.id in decoded.categoryIds }
            .flatMap(BackupCategory::tables)
            .forEach { descriptor ->
                val data = requireNotNull(decoded.tableData[descriptor.tableName])
                data.rows.forEach { row ->
                    val values = ContentValues(data.columns.size)
                    data.columns.zip(row).forEach { (column, value) ->
                        values.putSqlValue(column, value)
                    }
                    val result = database.insert(
                        descriptor.tableName,
                        SQLiteDatabase.CONFLICT_REPLACE,
                        values,
                    )
                    require(result != -1L)
                }
            }
    }

    private fun replaceFilesAndStores(decoded: DecodedPayload) {
        decoded.settings?.let { settings ->
            replaceNormalStoreTexts(settings.files.associate { it.filename to it.text })
        }
        if ("subscriptions" in decoded.categoryIds) {
            subsFolder.listFiles().orEmpty().filter(File::isFile).forEach(File::delete)
            decoded.subscriptionFiles.forEach { file ->
                writeFileAtomically(subsFolder.resolve(file.relativePath), decodeFileBytes(file))
            }
        }
        if ("sensitive_optional" in decoded.categoryIds) {
            snapshotFolder.listFiles().orEmpty().forEach(File::deleteRecursively)
            decoded.snapshotFiles.forEach { file ->
                val target = snapshotFolder.resolve(file.relativePath)
                require(target.canonicalPath.startsWith(snapshotFolder.canonicalPath + File.separator))
                writeFileAtomically(target, decodeFileBytes(file))
            }
        }
    }

    private fun keysByCategory(payload: BackupPayload): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        payload.objects.forEach { objectValue ->
            val keys = result.getOrPut(objectValue.categoryId) { mutableSetOf() }
            when {
                objectValue.objectId == SETTINGS_OBJECT_ID -> {
                    json.decodeFromString<BackupSettingsData>(objectValue.content.decodeToString())
                        .files.forEach { keys += "settings:${it.filename}" }
                }
                objectValue.objectId.startsWith("table-") -> {
                    val data = json.decodeFromString<BackupTableData>(
                        objectValue.content.decodeToString(),
                    )
                    val descriptor = requireNotNull(descriptorForTable(data.tableName))
                    val indices = descriptor.primaryKeyColumns.map(data.columns::indexOf)
                    data.rows.forEach { row ->
                        keys += data.tableName + ":" +
                            indices.joinToString("|") { row[it].stableKey() }
                    }
                }
                else -> {
                    val file = json.decodeFromString<BackupFileData>(
                        objectValue.content.decodeToString(),
                    )
                    keys += "file:${file.relativePath}"
                }
            }
        }
        return result
    }

    private fun decodeFile(
        objectValue: BackupPayloadObject,
        pathValidator: (String) -> Boolean,
    ): BackupFileData {
        val file = json.decodeFromString<BackupFileData>(objectValue.content.decodeToString())
        require(pathValidator(file.relativePath))
        require(objectValue.objectId.endsWith(shortHash(file.relativePath)))
        decodeFileBytes(file)
        return file
    }

    private fun decodeFileBytes(file: BackupFileData): ByteArray =
        Base64.getDecoder().decode(file.contentBase64)

    private fun Cursor.readSqlValue(columnIndex: Int): BackupSqlValue = when (getType(columnIndex)) {
        Cursor.FIELD_TYPE_NULL -> BackupSqlValue(BackupSqlValueType.NULL)
        Cursor.FIELD_TYPE_INTEGER -> BackupSqlValue(
            BackupSqlValueType.INTEGER,
            getLong(columnIndex).toString(),
        )
        Cursor.FIELD_TYPE_FLOAT -> BackupSqlValue(
            BackupSqlValueType.REAL,
            getDouble(columnIndex).toString(),
        )
        Cursor.FIELD_TYPE_STRING -> BackupSqlValue(
            BackupSqlValueType.TEXT,
            getString(columnIndex),
        )
        Cursor.FIELD_TYPE_BLOB -> BackupSqlValue(
            BackupSqlValueType.BLOB,
            Base64.getEncoder().encodeToString(getBlob(columnIndex)),
        )
        else -> error("unsupported_sql_value")
    }

    private fun ContentValues.putSqlValue(column: String, value: BackupSqlValue) {
        when (value.type) {
            BackupSqlValueType.NULL -> putNull(column)
            BackupSqlValueType.INTEGER -> put(column, value.value!!.toLong())
            BackupSqlValueType.REAL -> put(column, value.value!!.toDouble())
            BackupSqlValueType.TEXT -> put(column, value.value!!)
            BackupSqlValueType.BLOB -> put(column, Base64.getDecoder().decode(value.value))
        }
    }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(${quoteIdentifier(table)})").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private data class DecodedPayload(
        val categoryIds: Set<String>,
        val tableData: Map<String, BackupTableData>,
        val settings: BackupSettingsData?,
        val subscriptionFiles: List<BackupFileData>,
        val snapshotFiles: List<BackupFileData>,
    )

    companion object {
        private const val SETTINGS_OBJECT_ID = "settings"
        private val SUBSCRIPTION_PATH = Regex("-?\\d+\\.json")

        private fun categoryForTable(tableName: String): BackupCategory? =
            BackupCatalog.categories.firstOrNull { category ->
                category.tables.any { it.tableName == tableName }
            }

        private fun descriptorForTable(tableName: String): BackupTableDescriptor? =
            categoryForTable(tableName)?.tables?.firstOrNull { it.tableName == tableName }

        private fun quoteIdentifier(identifier: String): String =
            "\"${identifier.replace("\"", "\"\"")}\""

        private fun isSafeRelativeFile(path: String): Boolean =
            path.isNotBlank() &&
                path.length <= 240 &&
                '\\' !in path &&
                '\u0000' !in path &&
                !path.startsWith('/') &&
                path.split('/').none { it.isBlank() || it == "." || it == ".." }

        private fun isValidSnapshotPath(path: String): Boolean {
            if (!isSafeRelativeFile(path)) return false
            val segments = path.split('/')
            if (segments.size != 2 || segments[0].toLongOrNull() == null) return false
            val id = segments[0]
            return segments[1] == "$id.json" ||
                segments[1] == "$id.min.json" ||
                segments[1] == "$id.png"
        }

        private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }

        private fun BackupSqlValue.stableKey(): String = "${type.name}:${value.orEmpty()}"
    }
}
