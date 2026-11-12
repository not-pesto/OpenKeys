package io.github.openkeys.routing

import io.github.openkeys.midi.MidiNote

enum class VoiceSlotId { RIGHT_1, RIGHT_2, LEFT_1 }

data class SlotRouting(
    val enabled: Boolean = false,
    val transposeSemitones: Int = 0,
)

data class RoutingConfig(
    val splitEnabled: Boolean = false,
    val splitNote: Int = MidiNote.MIDDLE_C,
    val right1: SlotRouting = SlotRouting(enabled = true),
    val right2: SlotRouting = SlotRouting(),
    val left1: SlotRouting = SlotRouting(),
    val globalTranspose: Int = 0,
) {
    init {
        require(splitNote in MidiNote.MIN..MidiNote.MAX) { "Split note out of range: $splitNote" }
        require(globalTranspose in -12..12) { "Global transpose out of range: $globalTranspose" }
    }

    fun slot(id: VoiceSlotId): SlotRouting = when (id) {
        VoiceSlotId.RIGHT_1 -> right1
        VoiceSlotId.RIGHT_2 -> right2
        VoiceSlotId.LEFT_1 -> left1
    }
}

data class SlotNote(val slot: VoiceSlotId, val note: Int)

sealed interface RoutedEvent {
    val target: SlotNote

    data class NoteOn(override val target: SlotNote, val velocity: Int) : RoutedEvent
    data class NoteOff(override val target: SlotNote) : RoutedEvent
}
