package li.songe.gkd.sdp.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.data.AppConfig
import li.songe.gkd.sdp.data.CategoryConfig
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.util.ZipUtils
import li.songe.gkd.sdp.util.json
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile

/**
 * Reads the unencrypted backup layout used before v2. The importer never extracts or executes
 * archive entries: it accepts an exact path allowlist and converts the known data into the v2
 * catalog so the normal validation, conflict preview, journal and rollback path remains mandatory.
 */
object LegacyBackupImporter {
    private val knownStoreFiles = setOf(
        "store.json",
        "action_count.txt",
        "block_match_app_list.txt",
        "block_a11y_app_list.txt",
        "a11y_scope_app_list.txt",
        "terms_accepted.txt",
        "ignore_version_list.json",
    )
    private val subscriptionFile = Regex("subscription/-?\\d+\\.json")
    private val knownDirectories = setOf("store", "subscription")

    fun looksLikeArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() &&
            bytes[3] == 4.toByte()

    fun categoryForPath(rawPath: String): String? {
        if ('\\' in rawPath || '\u0000' in rawPath || rawPath.startsWith('/')) return null
        val segments = rawPath.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return when {
            rawPath == "db.json" -> "subscriptions"
            rawPath.startsWith("store/") &&
                rawPath.substringAfter("store/") in knownStoreFiles -> "settings"
            subscriptionFile.matches(rawPath) -> "subscriptions"
            else -> null
        }
    }

    fun read(archive: File): BackupResult<BackupPayload> = try {
        val entries = ZipUtils.validateArchive(archive)
        if (entries.isEmpty()) return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        if (entries.any { entry ->
                if (entry.isDirectory) {
                    entry.normalizedName !in knownDirectories
                } else {
                    categoryForPath(entry.normalizedName) == null
                }
            }
        ) {
            return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        }

        ZipFile(archive).use { zip ->
            val rawEntries = entries.filterNot(ZipUtils.ValidatedArchiveEntry::isDirectory)
                .associate { entry ->
                    entry.normalizedName to zip.getInputStream(zip.getEntry(entry.originalName))
                        .use { it.readBytes() }
                }
            try {
                convert(rawEntries)
            } finally {
                rawEntries.values.forEach { bytes -> bytes.fill(0) }
            }
        }
    } catch (_: Throwable) {
        BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
    }

    private fun convert(entries: Map<String, ByteArray>): BackupResult<BackupPayload> {
        val objects = mutableListOf<BackupPayloadObject>()
        val storeFiles = entries.entries
            .filter { (path) -> categoryForPath(path) == "settings" }
            .sortedBy { entry -> entry.key }
            .map { (path, content) ->
                BackupTextFile(
                    filename = path.substringAfter("store/"),
                    text = content.decodeToString(),
                )
            }
        if (storeFiles.isNotEmpty()) {
            objects += BackupPayloadObject(
                objectId = "settings",
                categoryId = "settings",
                schema = 1,
                count = storeFiles.size,
                content = json.encodeToString(BackupSettingsData(storeFiles)).encodeToByteArray(),
            )
        }

        val hasSubscriptionData = "db.json" in entries ||
            entries.keys.any(subscriptionFile::matches)
        if (hasSubscriptionData) {
            val database = entries["db.json"]?.let { bytes ->
                json.decodeFromString<LegacyDbData>(bytes.decodeToString())
            } ?: LegacyDbData()
            objects += database.toTableObjects()
            entries.entries
                .filter { (path) -> subscriptionFile.matches(path) }
                .sortedBy { entry -> entry.key }
                .forEach { (path, content) ->
                    val relativePath = path.substringAfter("subscription/")
                    val fileData = BackupFileData(
                        relativePath = relativePath,
                        contentBase64 = Base64.getEncoder().encodeToString(content),
                    )
                    objects += BackupPayloadObject(
                        objectId = "subscription-file-${shortHash(relativePath)}",
                        categoryId = "subscriptions",
                        schema = 1,
                        count = 1,
                        content = json.encodeToString(fileData).encodeToByteArray(),
                    )
                }
        }
        if (objects.isEmpty()) return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        return BackupResult.Success(BackupPayloadArchive.createPayload(objects))
    }

    @Serializable
    private data class LegacyDbData(
        val subsItems: List<SubsItem>? = null,
        val subsConfigs: List<SubsConfig>? = null,
        val categoryConfigs: List<CategoryConfig>? = null,
        val appConfigs: List<AppConfig>? = null,
    ) {
        fun toTableObjects(): List<BackupPayloadObject> = listOf(
            tableObject(
                tableName = "subs_item",
                columns = listOf(
                    "id",
                    "ctime",
                    "mtime",
                    "enable",
                    "enable_update",
                    "order",
                    "update_url",
                ),
                rows = subsItems.orEmpty().sortedBy(SubsItem::id).map { item ->
                    listOf(
                        integer(item.id),
                        integer(item.ctime),
                        integer(item.mtime),
                        integer(item.enable),
                        integer(item.enableUpdate),
                        integer(item.order),
                        text(item.updateUrl),
                    )
                },
            ),
            tableObject(
                tableName = "subs_config",
                columns = listOf(
                    "id",
                    "type",
                    "enable",
                    "subs_id",
                    "app_id",
                    "group_key",
                    "exclude",
                ),
                rows = subsConfigs.orEmpty().sortedBy(SubsConfig::id).map { item ->
                    listOf(
                        integer(item.id),
                        integer(item.type),
                        nullableBoolean(item.enable),
                        integer(item.subsId),
                        text(item.appId),
                        integer(item.groupKey),
                        text(item.exclude),
                    )
                },
            ),
            tableObject(
                tableName = "category_config",
                columns = listOf("id", "enable", "subs_id", "category_key"),
                rows = categoryConfigs.orEmpty().sortedBy(CategoryConfig::id).map { item ->
                    listOf(
                        integer(item.id),
                        nullableBoolean(item.enable),
                        integer(item.subsId),
                        integer(item.categoryKey),
                    )
                },
            ),
            tableObject(
                tableName = "app_config",
                columns = listOf("id", "enable", "subs_id", "app_id"),
                rows = appConfigs.orEmpty().sortedBy(AppConfig::id).map { item ->
                    listOf(
                        integer(item.id),
                        integer(item.enable),
                        integer(item.subsId),
                        text(item.appId),
                    )
                },
            ),
        )
    }

    private fun tableObject(
        tableName: String,
        columns: List<String>,
        rows: List<List<BackupSqlValue>>,
    ): BackupPayloadObject = BackupPayloadObject(
        objectId = "table-$tableName",
        categoryId = "subscriptions",
        schema = 1,
        count = rows.size,
        content = json.encodeToString(BackupTableData(tableName, columns, rows))
            .encodeToByteArray(),
    )

    private fun integer(value: Number): BackupSqlValue =
        BackupSqlValue(BackupSqlValueType.INTEGER, value.toLong().toString())

    private fun integer(value: Boolean): BackupSqlValue = integer(if (value) 1 else 0)

    private fun nullableBoolean(value: Boolean?): BackupSqlValue =
        value?.let(::integer) ?: nullValue()

    private fun text(value: String?): BackupSqlValue = value?.let {
        BackupSqlValue(BackupSqlValueType.TEXT, it)
    } ?: nullValue()

    private fun nullValue(): BackupSqlValue = BackupSqlValue(BackupSqlValueType.NULL)

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
