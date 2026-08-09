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
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import li.songe.gkd.sdp.backup.BackupDataMutationParticipant
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
) : MutableStateFlow<T> by stateFlow, BackupDataMutationParticipant {
    private val mutationLock = Any()
    private var deferredMutations: MutableList<(T) -> T>? = null
    private var projectedValue: Any? = NO_PROJECTED_VALUE
    private var replacementValue: Any? = NO_REPLACEMENT_VALUE

    init {
        BackupDataMutationBarrier.register(this)
    }

    override var value: T
        get() = synchronized(mutationLock) { projectedValueOrCommitted() }
        set(value) = mutateOrDefer { value }

    override fun compareAndSet(expect: T, update: T): Boolean = synchronized(mutationLock) {
        if (deferredMutations == null) {
            stateFlow.compareAndSet(expect, update)
        } else if (projectedValueOrCommitted() != expect) {
            false
        } else {
            deferredMutations?.add { update }
            projectedValue = update
            true
        }
    }

    override suspend fun emit(value: T) {
        mutateOrDefer { value }
    }

    override fun tryEmit(value: T): Boolean {
        mutateOrDefer { value }
        return true
    }

    fun update(function: (T) -> T) = mutateOrDefer(function)

    fun encodeSelf(): String = synchronized(mutationLock) {
        encode(stateFlow.value)
    }

    fun updateByDecode(text: String?) {
        synchronized(mutationLock) {
            val decoded = decode(text)
            val mutations = deferredMutations
            if (mutations == null) {
                stateFlow.value = decoded
            } else {
                replacementValue = decoded
                projectedValue = mutations.fold(decoded) { value, mutation -> mutation(value) }
            }
        }
    }

    override fun beginConsistentSnapshot() {
        synchronized(mutationLock) {
            check(deferredMutations == null)
            deferredMutations = mutableListOf()
            projectedValue = stateFlow.value
            replacementValue = NO_REPLACEMENT_VALUE
        }
    }

    override fun finishConsistentSnapshot() {
        synchronized(mutationLock) {
            val mutations = deferredMutations ?: return
            try {
                @Suppress("UNCHECKED_CAST")
                var replayed = if (replacementValue === NO_REPLACEMENT_VALUE) {
                    stateFlow.value
                } else {
                    replacementValue as T
                }
                mutations.forEach { mutation -> replayed = mutation(replayed) }
                stateFlow.value = replayed
            } finally {
                deferredMutations = null
                projectedValue = NO_PROJECTED_VALUE
                replacementValue = NO_REPLACEMENT_VALUE
            }
        }
    }

    private fun mutateOrDefer(function: (T) -> T) {
        synchronized(mutationLock) {
            val pending = deferredMutations
            if (pending == null) {
                stateFlow.value = function(stateFlow.value)
            } else {
                projectedValue = function(projectedValueOrCommitted())
                pending += function
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun projectedValueOrCommitted(): T =
        if (projectedValue === NO_PROJECTED_VALUE) stateFlow.value else projectedValue as T

    private companion object {
        private val NO_PROJECTED_VALUE = Any()
        private val NO_REPLACEMENT_VALUE = Any()
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
            BackupDataMutationBarrier.withMutation {
                withContext(Dispatchers.IO) {
                    writeTextAtomically(file, encode(it))
                }
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
