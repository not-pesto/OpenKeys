package io.github.openkeys.soundfonts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class SoundFontEntry(
    val id: String,
    val name: String,
    val fileName: String,
    val instrumentNames: Map<String, String> = emptyMap(),
    val sourceFileName: String = name,
) {
    fun instrumentName(bank: Int, program: Int, original: String): String =
        instrumentNames["$bank:$program"] ?: original
}

/** Imported SoundFonts, copied into app storage so they survive restarts without URI permissions. */
class SoundFontLibrary(
    private val dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val index = File(dir, "index.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<SoundFontEntry>>(emptyList())
    val entries: StateFlow<List<SoundFontEntry>> = _entries.asStateFlow()

    suspend fun load() = mutex.withLock {
        withContext(io) {
            _entries.value = try {
                if (index.exists()) json.decodeFromString<List<SoundFontEntry>>(index.readText()) else emptyList()
            } catch (e: SerializationException) {
                emptyList()
            }.filter { file(it).exists() }
        }
    }

    fun file(entry: SoundFontEntry) = File(dir, entry.fileName)

    fun file(id: String): File? = entries.value.firstOrNull { it.id == id }?.let(::file)

    fun newFile(id: String): File = File(dir.apply { mkdirs() }, "$id.sf")

    suspend fun add(entry: SoundFontEntry) = mutex.withLock {
        val next = entries.value.filterNot { it.id == entry.id } + entry
        withContext(io) { write(next) }
        _entries.value = next
    }

    suspend fun rename(id: String, name: String) = update(id) { entry ->
        require(name.isNotBlank()) { "Name cannot be blank" }
        entry.copy(name = name.trim())
    }

    suspend fun renameInstrument(id: String, bank: Int, program: Int, name: String?) = update(id) { entry ->
        require(bank >= 0 && program in 0..127) { "Invalid bank or program" }
        require(name == null || name.isNotBlank()) { "Name cannot be blank" }
        val key = "$bank:$program"
        val names = if (name == null) entry.instrumentNames - key else entry.instrumentNames + (key to name.trim())
        entry.copy(instrumentNames = names)
    }

    private suspend fun update(id: String, transform: (SoundFontEntry) -> SoundFontEntry) = mutex.withLock {
        val entry = entries.value.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("SoundFont not found")
        val updated = transform(entry)
        val next = entries.value.map { if (it.id == id) updated else it }
        withContext(io) { write(next) }
        _entries.value = next
    }

    suspend fun remove(id: String) = mutex.withLock {
        val entry = entries.value.firstOrNull { it.id == id } ?: return@withLock
        val next = entries.value - entry
        withContext(io) {
            write(next)
            file(entry).delete()
        }
        _entries.value = next
    }

    private fun write(list: List<SoundFontEntry>) {
        dir.mkdirs()
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(list))
        Files.move(tmp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
