package io.github.openkeys.routing

import io.github.openkeys.midi.MidiNote

/**
 * Maps keyboard notes to voice slots. Note-offs release exactly what the matching note-on
 * started, so config changes while keys are held cannot leave stuck notes. Not thread-safe.
 */
class NoteRouter(config: RoutingConfig = RoutingConfig()) {

    var config: RoutingConfig = config

    private val held = arrayOfNulls<List<SlotNote>>(MidiNote.MAX + 1)

    fun noteOn(note: Int, velocity: Int): List<RoutedEvent> {
        requireNote(note)
        require(velocity in 0..127) { "Velocity out of range: $velocity" }
        if (velocity == 0) return noteOff(note)

        val events = mutableListOf<RoutedEvent>()
        held[note]?.forEach { events += RoutedEvent.NoteOff(it) }

        val targets = targetsFor(note)
        targets.forEach { events += RoutedEvent.NoteOn(it, velocity) }
        held[note] = targets.ifEmpty { null }
        return events
    }

    fun noteOff(note: Int): List<RoutedEvent> {
        requireNote(note)
        val targets = held[note] ?: return emptyList()
        held[note] = null
        return targets.map { RoutedEvent.NoteOff(it) }
    }

    fun allNotesOff(): List<RoutedEvent> {
        val events = mutableListOf<RoutedEvent>()
        for (i in held.indices) {
            held[i]?.forEach { events += RoutedEvent.NoteOff(it) }
            held[i] = null
        }
        return events
    }

    private fun targetsFor(note: Int): List<SlotNote> {
        val c = config
        val slots = when {
            !c.splitEnabled -> listOf(VoiceSlotId.RIGHT_1, VoiceSlotId.RIGHT_2)
            note < c.splitNote -> listOf(VoiceSlotId.LEFT_1)
            else -> listOf(VoiceSlotId.RIGHT_1, VoiceSlotId.RIGHT_2)
        }
        return slots.mapNotNull { id ->
            val slot = c.slot(id)
            val out = note + slot.transposeSemitones
            if (slot.enabled && out in MidiNote.MIN..MidiNote.MAX) SlotNote(id, out) else null
        }
    }

    private fun requireNote(note: Int) =
        require(note in MidiNote.MIN..MidiNote.MAX) { "MIDI note out of range: $note" }
}
