package io.github.openkeys.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiNoteTest {
    @Test
    fun namesUseMiddleCAsC4() {
        assertEquals("C4", MidiNote.name(MidiNote.MIDDLE_C))
        assertEquals("C-1", MidiNote.name(MidiNote.MIN))
        assertEquals("G9", MidiNote.name(MidiNote.MAX))
        assertEquals("F#3", MidiNote.name(54))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeNote() {
        MidiNote.name(128)
    }
}
