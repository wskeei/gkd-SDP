package li.songe.gkd.sdp.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupSettingsData(
    val files: List<BackupTextFile>,
)

@Serializable
data class BackupTextFile(
    val filename: String,
    val text: String,
)

@Serializable
data class BackupFileData(
    val relativePath: String,
    val contentBase64: String,
)

@Serializable
data class BackupTableData(
    val tableName: String,
    val columns: List<String>,
    val rows: List<List<BackupSqlValue>>,
)

@Serializable
data class BackupSqlValue(
    val type: BackupSqlValueType,
    val value: String? = null,
)

@Serializable
enum class BackupSqlValueType {
    NULL,
    INTEGER,
    REAL,
    TEXT,
    BLOB,
}
