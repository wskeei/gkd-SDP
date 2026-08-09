package li.songe.gkd.sdp.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.privateStoreFolder
import li.songe.gkd.sdp.util.storeFolder
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap


private fun readStoreText(
    file: File
): String? = file.run {
    if (exists()) {
        readText()
    } else {
        null
    }
}

private val normalStoreRegistry = ConcurrentHashMap<String, MutableStoreStateFlow<*>>()

fun writeFileAtomically(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val tempFile = File("${file.absolutePath}.tmp")
    tempFile.outputStream().use {
        it.write(bytes)
        it.flush()
        it.fd.sync()
    }
    try {
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

fun writeTextAtomically(file: File, text: String) =
    writeFileAtomically(file, text.toByteArray(Charsets.UTF_8))

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MutableStoreStateFlow<T>(
    val filename: String,
    val decode: (String?) -> T,
    val encode: (T) -> String,
    private val stateFlow: MutableStateFlow<T>,
) : MutableStateFlow<T> by stateFlow {
    fun encodeSelf(): String = encode(value)
    fun updateByDecode(text: String?) {
        value = decode(text)
    }
}

fun <T> createTextFlow(
    key: String,
    decode: (String?) -> T,
    encode: (T) -> String,
    private: Boolean = false,
    scope: CoroutineScope = appScope,
    debounceMillis: Long = 0,
): MutableStoreStateFlow<T> {
    val filename = if (key.contains('.')) key else "$key.txt"
    val file = (if (private) privateStoreFolder else storeFolder).resolve(filename)
    val initText = readStoreText(file)
    val initValue = decode(initText)
    val stateFlow = MutableStateFlow(initValue)
    scope.launch {
        stateFlow.drop(1).conflate().debounce(debounceMillis).collect {
            withContext(Dispatchers.IO) {
                writeTextAtomically(file, encode(it))
            }
        }
    }
    return MutableStoreStateFlow(
        filename = filename,
        decode = decode,
        encode = encode,
        stateFlow = stateFlow,
    ).also { flow ->
        if (!private) normalStoreRegistry[filename] = flow
    }
}

fun snapshotNormalStoreTexts(): Map<String, String> = buildMap {
    storeFolder.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".tmp") }
        .sortedBy(File::getName)
        .forEach { file -> put(file.name, file.readText()) }
    normalStoreRegistry.toSortedMap().forEach { (filename, flow) ->
        put(filename, flow.encodeSelf())
    }
}

fun replaceNormalStoreTexts(values: Map<String, String>) {
    require(values.keys.all(::isSafeStoreFilename))
    storeFolder.listFiles().orEmpty()
        .filter(File::isFile)
        .forEach(File::delete)
    values.toSortedMap().forEach { (filename, text) ->
        writeTextAtomically(storeFolder.resolve(filename), text)
    }
    normalStoreRegistry.forEach { (filename, flow) ->
        flow.updateByDecode(values[filename])
    }
}

private fun isSafeStoreFilename(filename: String): Boolean =
    filename.isNotBlank() &&
        filename.length <= 120 &&
        filename.none { it == '/' || it == '\\' || it == '\u0000' } &&
        filename != "." &&
        filename != ".." &&
        !filename.endsWith(".tmp")

inline fun <reified T> createAnyFlow(
    key: String,
    crossinline default: () -> T,
    crossinline initialize: (T) -> T = { it },
    private: Boolean = false,
    scope: CoroutineScope = appScope,
    debounceMillis: Long = 0,
): MutableStoreStateFlow<T> {
    return createTextFlow(
        key = "$key.json",
        decode = {
            val initValue = it?.let {
                runCatching { json.decodeFromString<T>(it) }.getOrNull()
            }
            initialize(initValue ?: default())
        },
        encode = {
            json.encodeToString(it)
        },
        private = private,
        scope = scope,
        debounceMillis = debounceMillis,
    )
}
