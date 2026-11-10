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
data class SoundFontEntry(val id: String, val name: String, val fileName: String)

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

    private fun write(list: List<SoundFontEntry>) {
        dir.mkdirs()
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(list))
        Files.move(tmp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
