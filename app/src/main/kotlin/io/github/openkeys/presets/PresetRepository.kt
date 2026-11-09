package io.github.openkeys.presets

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Preset library persisted as one JSON file. All I/O runs on [io], off the audio and UI threads. */
class PresetRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private val _presets = MutableStateFlow<List<PerformancePreset>>(emptyList())
    val presets: StateFlow<List<PerformancePreset>> = _presets.asStateFlow()

    /** Returns descriptions of presets that could not be loaded. */
    suspend fun load(): List<String> = mutex.withLock {
        withContext(io) {
            if (!file.exists()) return@withContext emptyList()
            val decoded = PresetCodec.decode(file.readText())
            _presets.value = decoded.presets
            decoded.rejected
        }
    }

    suspend fun create(name: String, template: PerformancePreset? = null): PerformancePreset {
        val preset = (template ?: PerformancePreset(id = "", name = name)).copy(id = newId(), name = name.trim())
        update { it + preset }
        return preset
    }

    suspend fun save(preset: PerformancePreset) = update { list ->
        if (list.any { it.id == preset.id }) list.map { if (it.id == preset.id) preset else it } else list + preset
    }

    suspend fun rename(id: String, name: String) = update { list ->
        list.map { if (it.id == id) it.copy(name = name.trim()) else it }
    }

    suspend fun duplicate(id: String, name: String): PerformancePreset {
        val source = presets.value.firstOrNull { it.id == id } ?: throw NoSuchElementException("No preset $id")
        return create(name, source)
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(transform: (List<PerformancePreset>) -> List<PerformancePreset>) = mutex.withLock {
        val next = transform(_presets.value)
        next.forEach { preset ->
            val problems = preset.problems()
            require(problems.isEmpty()) { problems.joinToString() }
        }
        withContext(io) { writeAtomically(PresetCodec.encode(next)) }
        _presets.value = next
    }

    private fun writeAtomically(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
