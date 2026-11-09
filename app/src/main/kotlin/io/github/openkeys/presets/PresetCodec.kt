package io.github.openkeys.presets

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PresetFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Versioned JSON format for the preset library. Invalid presets are skipped, not fatal. */
object PresetCodec {
    const val SCHEMA_VERSION = 1

    @Serializable
    private data class PresetFile(val schemaVersion: Int, val presets: List<PerformancePreset>)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    data class Decoded(val presets: List<PerformancePreset>, val rejected: List<String>)

    fun encode(presets: List<PerformancePreset>): String =
        json.encodeToString(PresetFile.serializer(), PresetFile(SCHEMA_VERSION, presets))

    fun decode(text: String): Decoded {
        try {
            val version = json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.int
                ?: throw PresetFormatException("Missing schemaVersion")
            if (version > SCHEMA_VERSION) {
                throw PresetFormatException("Presets were saved by a newer app version ($version)")
            }
            // Migrations from older versions go here as the schema evolves.
            val file = json.decodeFromString(PresetFile.serializer(), text)
            val rejected = mutableListOf<String>()
            val seenIds = mutableSetOf<String>()
            val valid = file.presets.filter { preset ->
                val problems = preset.problems() + if (!seenIds.add(preset.id)) listOf("Duplicate id") else emptyList()
                if (problems.isNotEmpty()) rejected += "${preset.name.ifBlank { preset.id }}: ${problems.joinToString()}"
                problems.isEmpty()
            }
            return Decoded(valid, rejected)
        } catch (e: SerializationException) {
            throw PresetFormatException("Preset file is corrupt", e)
        } catch (e: IllegalArgumentException) {
            throw PresetFormatException("Preset file is corrupt", e)
        }
    }
}
