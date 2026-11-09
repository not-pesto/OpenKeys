package io.github.openkeys.midi

import io.github.openkeys.midi.MidiEvent.ControlChange
import io.github.openkeys.midi.MidiEvent.NoteOff
import io.github.openkeys.midi.MidiEvent.NoteOn
import io.github.openkeys.midi.MidiEvent.PitchBend
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiParserTest {

    private fun parse(vararg chunks: IntArray): List<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        val parser = MidiParser { events += it }
        chunks.forEach { chunk -> parser.feed(ByteArray(chunk.size) { chunk[it].toByte() }) }
        return events
    }

    @Test
    fun parsesNoteOnAndNoteOff() {
        assertEquals(
            listOf(NoteOn(0, 60, 100), NoteOff(2, 60, 64)),
            parse(intArrayOf(0x90, 60, 100, 0x82, 60, 64)),
        )
    }

    @Test
    fun noteOnWithZeroVelocityIsNoteOff() {
        assertEquals(listOf(NoteOff(0, 60, 0)), parse(intArrayOf(0x90, 60, 0)))
    }

    @Test
    fun supportsRunningStatus() {
        assertEquals(
            listOf(NoteOn(0, 60, 100), NoteOn(0, 64, 90), NoteOff(0, 60, 0)),
            parse(intArrayOf(0x90, 60, 100, 64, 90, 60, 0)),
        )
    }

    @Test
    fun messagesMaySpanChunks() {
        assertEquals(listOf(NoteOn(0, 60, 100)), parse(intArrayOf(0x90), intArrayOf(60), intArrayOf(100)))
    }

    @Test
    fun realTimeBytesDoNotBreakMessages() {
        assertEquals(
            listOf(NoteOn(0, 60, 100), NoteOn(0, 62, 100)),
            parse(intArrayOf(0x90, 0xF8, 60, 0xFE, 100, 62, 0xF8, 100)),
        )
    }

    @Test
    fun skipsSysExAndProgramChange() {
        assertEquals(
            listOf(NoteOn(1, 48, 80)),
            parse(intArrayOf(0xF0, 0x7E, 0x00, 0x06, 0x01, 0xF7, 0xC1, 5, 0x91, 48, 80)),
        )
    }

    @Test
    fun systemCommonCancelsRunningStatus() {
        assertEquals(listOf(NoteOn(0, 60, 100)), parse(intArrayOf(0x90, 60, 100, 0xF3, 1, 62, 100)))
    }

    @Test
    fun parsesControlChangeAndPitchBend() {
        assertEquals(
            listOf(ControlChange(0, 64, 127), PitchBend(0, 0), PitchBend(0, -8192), PitchBend(0, 8191)),
            parse(intArrayOf(0xB0, 64, 127, 0xE0, 0x00, 0x40, 0xE0, 0, 0, 0xE0, 0x7F, 0x7F)),
        )
    }
}
