package io.github.openkeys.presets

import io.github.openkeys.midi.MidiNote
import io.github.openkeys.routing.RoutingConfig
import io.github.openkeys.routing.SlotRouting
import io.github.openkeys.routing.VoiceSlotId
import kotlinx.serialization.Serializable

@Serializable
data class VoiceSlot(
    val enabled: Boolean = false,
    val soundFontId: String? = null,
    /** 14-bit MIDI bank (MSB * 128 + LSB), as used by FluidSynth. */
    val bank: Int = 0,
    val program: Int = 0,
    val volume: Float = 0.8f,
    val transposeSemitones: Int = 0,
) {
    fun problems(label: String): List<String> = buildList {
        if (bank !in 0..16383) add("$label: bank $bank out of range")
        if (program !in 0..127) add("$label: program $program out of range")
        if (volume !in 0f..1f) add("$label: volume $volume out of range")
        if (transposeSemitones !in -MAX_TRANSPOSE..MAX_TRANSPOSE) add("$label: transpose $transposeSemitones out of range")
    }

    fun toRouting() = SlotRouting(enabled, transposeSemitones)

    companion object {
        const val MAX_TRANSPOSE = 36
    }
}

@Serializable
data class PerformancePreset(
    val id: String,
    val name: String,
    val splitEnabled: Boolean = false,
    val splitNote: Int = MidiNote.MIDDLE_C,
    val right1: VoiceSlot = VoiceSlot(enabled = true),
    val right2: VoiceSlot = VoiceSlot(),
    val left1: VoiceSlot = VoiceSlot(),
    val masterReverb: Float = 0.3f,
    val masterChorus: Float = 0.0f,
) {
    fun problems(): List<String> = buildList {
        if (id.isBlank()) add("Missing id")
        if (name.isBlank()) add("Missing name")
        if (splitNote !in MidiNote.MIN..MidiNote.MAX) add("Split note $splitNote out of range")
        if (masterReverb !in 0f..1f) add("Reverb $masterReverb out of range")
        if (masterChorus !in 0f..1f) add("Chorus $masterChorus out of range")
        addAll(right1.problems("Right 1"))
        addAll(right2.problems("Right 2"))
        addAll(left1.problems("Left 1"))
    }

    fun soundFontIds(): Set<String> = listOfNotNull(right1.soundFontId, right2.soundFontId, left1.soundFontId).toSet()

    fun slot(id: VoiceSlotId): VoiceSlot = when (id) {
        VoiceSlotId.RIGHT_1 -> right1
        VoiceSlotId.RIGHT_2 -> right2
        VoiceSlotId.LEFT_1 -> left1
    }

    fun withSlot(id: VoiceSlotId, slot: VoiceSlot): PerformancePreset = when (id) {
        VoiceSlotId.RIGHT_1 -> copy(right1 = slot)
        VoiceSlotId.RIGHT_2 -> copy(right2 = slot)
        VoiceSlotId.LEFT_1 -> copy(left1 = slot)
    }

    fun toRoutingConfig() = RoutingConfig(
        splitEnabled = splitEnabled,
        splitNote = splitNote,
        right1 = right1.toRouting(),
        right2 = right2.toRouting(),
        left1 = left1.toRouting(),
    )
}
