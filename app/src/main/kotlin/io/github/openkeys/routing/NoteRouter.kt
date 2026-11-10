package io.github.openkeys.routing

import io.github.openkeys.midi.MidiNote

/**
 * Maps keyboard notes to voice slots. Split region is decided from the incoming key, then each slot's
 * transpose is applied. Note-offs release exactly what the matching note-on started, so config
 * changes while keys are held cannot leave stuck notes. Not thread-safe.
 */
class NoteRouter(config: RoutingConfig = RoutingConfig()) {

    var config: RoutingConfig = config

    private val held = arrayOfNulls<List<SlotNote>>(MidiNote.MAX + 1)

    // Keys held per (slot, output note); two keys can reach the same output after a transpose change.
    private val active = IntArray(VoiceSlotId.entries.size * (MidiNote.MAX + 1))

    fun noteOn(note: Int, velocity: Int): List<RoutedEvent> {
        requireNote(note)
        require(velocity in 0..127) { "Velocity out of range: $velocity" }
        if (velocity == 0) return noteOff(note)

        val events = mutableListOf<RoutedEvent>()
        held[note]?.let { release(it, events) }

        val targets = targetsFor(note)
        targets.forEach {
            active[index(it)]++
            events += RoutedEvent.NoteOn(it, velocity)
        }
        held[note] = targets.ifEmpty { null }
        return events
    }

    fun noteOff(note: Int): List<RoutedEvent> {
        requireNote(note)
        val targets = held[note] ?: return emptyList()
        held[note] = null
        return mutableListOf<RoutedEvent>().also { release(targets, it) }
    }

    fun allNotesOff(): List<RoutedEvent> {
        val events = mutableListOf<RoutedEvent>()
        for (i in held.indices) {
            held[i]?.let { release(it, events) }
            held[i] = null
        }
        active.fill(0)
        return events
    }

    private fun release(targets: List<SlotNote>, into: MutableList<RoutedEvent>) {
        for (t in targets) {
            val i = index(t)
            if (active[i] > 0 && --active[i] == 0) into += RoutedEvent.NoteOff(t)
        }
    }

    private fun index(t: SlotNote) = t.slot.ordinal * (MidiNote.MAX + 1) + t.note

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
