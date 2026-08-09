package li.songe.gkd.sdp.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.songe.gkd.sdp.util.ZipUtils
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class BackupPayloadObject(
    val objectId: String,
    val categoryId: String,
    val schema: Int,
    val count: Int,
    val content: ByteArray,
)

@Serializable
data class BackupManifestObject(
    val objectId: String,
    val categoryId: String,
    val schema: Int,
    val count: Int,
    val bytes: Long,
    val sha256: String,
    val entryName: String,
)

@Serializable
data class BackupPayloadManifest(
    val formatVersion: Int,
    val categoryIds: List<String>,
    val objects: List<BackupManifestObject>,
)

@Serializable
data class BackupPayload(
    val manifest: BackupPayloadManifest,
    val objects: List<BackupPayloadObject>,
) {
    val payloadHash: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        objects.sortedBy(BackupPayloadObject::objectId).forEach { objectValue ->
            digest.update(objectValue.objectId.encodeToByteArray())
            digest.update(0.toByte())
            digest.update(objectValue.categoryId.encodeToByteArray())
            digest.update(0.toByte())
            digest.update(sha256(objectValue.content).encodeToByteArray())
            digest.update(0.toByte())
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

object BackupPayloadArchive {
    private const val OBJECT_SCHEMA = 1
    private val objectIdPattern = Regex("[a-z0-9][a-z0-9_.-]{0,119}")
    private val codec = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun build(outputFile: File, rawObjects: Collection<BackupPayloadObject>): File {
        val payload = createPayload(rawObjects)
        val objects = payload.objects
        val manifest = payload.manifest
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream().buffered()).use { output ->
            writeEntry(output, "manifest.json", codec.encodeToString(manifest).encodeToByteArray())
            manifest.objects.zip(objects).forEach { (manifestObject, objectValue) ->
                writeEntry(output, manifestObject.entryName, objectValue.content)
            }
        }
        ZipUtils.validateArchive(outputFile)
        return outputFile
    }

    fun createPayload(rawObjects: Collection<BackupPayloadObject>): BackupPayload {
        val objects = rawObjects.sortedBy(BackupPayloadObject::objectId)
        require(objects.map(BackupPayloadObject::objectId).distinct().size == objects.size)
        objects.forEach { validateObject(it) }
        val manifestObjects = objects.map { objectValue ->
            BackupManifestObject(
                objectId = objectValue.objectId,
                categoryId = objectValue.categoryId,
                schema = objectValue.schema,
                count = objectValue.count,
                bytes = objectValue.content.size.toLong(),
                sha256 = sha256(objectValue.content),
                entryName = "objects/${objectValue.objectId}.bin",
            )
        }
        val selectedCategoryIds = BackupCatalog.categories.map(BackupCategory::id)
            .filter { categoryId -> objects.any { it.categoryId == categoryId } }
        return BackupPayload(
            manifest = BackupPayloadManifest(
                formatVersion = BackupFormatV2.FORMAT_VERSION,
                categoryIds = selectedCategoryIds,
                objects = manifestObjects,
            ),
            objects = objects,
        )
    }

    fun read(inputFile: File): BackupResult<BackupPayload> {
        return try {
            val validatedEntries = ZipUtils.validateArchive(inputFile)
            val validatedNames = validatedEntries.mapTo(mutableSetOf()) { it.originalName }
            if ("manifest.json" !in validatedNames) {
                return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
            }
            ZipFile(inputFile).use { zip ->
                val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use { input ->
                    codec.decodeFromString<BackupPayloadManifest>(input.readBytes().decodeToString())
                }
                if (manifest.formatVersion != BackupFormatV2.FORMAT_VERSION) {
                    return BackupResult.Failure(BackupErrorCode.UNSUPPORTED_VERSION)
                }
                if (
                    manifest.objects != manifest.objects.sortedBy(BackupManifestObject::objectId) ||
                    manifest.objects.map(BackupManifestObject::objectId).distinct().size !=
                    manifest.objects.size ||
                    manifest.categoryIds.any { BackupCatalog.category(it) == null }
                ) {
                    return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
                }
                if (
                    manifest.categoryIds.toSet() !=
                    manifest.objects.mapTo(mutableSetOf(), BackupManifestObject::categoryId)
                ) {
                    return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
                }
                val expectedNames = manifest.objects
                    .mapTo(mutableSetOf(), BackupManifestObject::entryName)
                    .apply { add("manifest.json") }
                if (expectedNames != validatedNames) {
                    return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
                }
                val objects = manifest.objects.map { item ->
                    if (
                        !objectIdPattern.matches(item.objectId) ||
                        item.entryName != "objects/${item.objectId}.bin" ||
                        item.schema != OBJECT_SCHEMA ||
                        item.count < 0 ||
                        BackupCatalog.category(item.categoryId) == null
                    ) {
                        return BackupResult.Failure(BackupErrorCode.SCHEMA_MISMATCH)
                    }
                    val content = zip.getInputStream(zip.getEntry(item.entryName)).use { it.readBytes() }
                    if (
                        content.size.toLong() != item.bytes ||
                        sha256(content) != item.sha256
                    ) {
                        return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
                    }
                    BackupPayloadObject(
                        objectId = item.objectId,
                        categoryId = item.categoryId,
                        schema = item.schema,
                        count = item.count,
                        content = content,
                    )
                }
                BackupResult.Success(BackupPayload(manifest, objects))
            }
        } catch (_: Throwable) {
            BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        }
    }

    private fun validateObject(objectValue: BackupPayloadObject) {
        require(objectIdPattern.matches(objectValue.objectId))
        require(BackupCatalog.category(objectValue.categoryId) != null)
        require(objectValue.schema == OBJECT_SCHEMA)
        require(objectValue.count >= 0)
        require(objectValue.content.size <= ZipUtils.ArchiveLimits().maxEntryUncompressedBytes)
    }

    private fun writeEntry(output: ZipOutputStream, name: String, content: ByteArray) {
        output.putNextEntry(ZipEntry(name).apply { time = 0L })
        output.write(content)
        output.closeEntry()
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }
