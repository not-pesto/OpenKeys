package io.github.openkeys.routing

import io.github.openkeys.presets.PerformancePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiEchoTest {

    private val sent = mutableListOf<Triple<Int, Int, Int>>()
    private val echo = MidiEcho { s, d1, d2 -> sent += Triple(s, d1, d2) }

    @Test
    fun routedNotesUseSlotChannels() {
        echo.routed(
            listOf(
                RoutedEvent.NoteOn(SlotNote(VoiceSlotId.RIGHT_1, 60), 100),
                RoutedEvent.NoteOn(SlotNote(VoiceSlotId.LEFT_1, 48), 90),
                RoutedEvent.NoteOff(SlotNote(VoiceSlotId.RIGHT_2, 72)),
            ),
        )
        assertEquals(listOf(Triple(0x90, 60, 100), Triple(0x92, 48, 90), Triple(0x81, 72, 0)), sent)
    }

    @Test
    fun pitchBendIsSplitInto7BitBytes() {
        echo.pitchBend(0)
        assertEquals(Triple(0xE0, 0, 64), sent.first())
        sent.clear()
        echo.pitchBend(8191)
        assertEquals(Triple(0xE0, 127, 127), sent.first())
    }

    @Test
    fun setupSendsOnlyChanges() {
        val p = PerformancePreset(id = "a", name = "A")
        echo.setup(p)
        assertEquals(6, sent.size)
        sent.clear()
        echo.setup(p.copy(right2 = p.right2.copy(program = 48)))
        assertEquals(listOf(Triple(0xC1, 48, 0)), sent)
        sent.clear()
        echo.setup(p, force = true)
        assertEquals(6, sent.size)
    }

    @Test
    fun drumBankSkipsProgramChange() {
        val p = PerformancePreset(id = "a", name = "A")
        echo.setup(p.copy(right1 = p.right1.copy(bank = 128, program = 0)))
        assertTrue(sent.none { it.first == 0xC0 })
    }

    @Test
    fun silenceReleasesAllChannels() {
        echo.silence()
        assertTrue(sent.containsAll(listOf(Triple(0xB0, 123, 0), Triple(0xB1, 123, 0), Triple(0xB2, 123, 0))))
        assertTrue(sent.containsAll(listOf(Triple(0xB0, 64, 0), Triple(0xB1, 64, 0), Triple(0xB2, 64, 0))))
    }
}
