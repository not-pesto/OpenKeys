package io.github.openkeys

import io.github.openkeys.audio.Patch
import io.github.openkeys.audio.SynthEngine
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.presets.PerformancePreset
import io.github.openkeys.presets.PresetRepository
import io.github.openkeys.routing.VoiceSlotId
import io.github.openkeys.soundfonts.SoundFontLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PerformanceControllerSustainTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun sustainAndNoteOffReachEveryRoutedSlotInOrder() {
        val synth = RecordingSynth()
        val controller = controller(synth)
        controller.update {
            it.copy(
                splitNote = 60,
                right2 = it.right2.copy(enabled = true),
                left1 = it.left1.copy(enabled = true),
            )
        }

        controller.onMidiEvent(MidiEvent.ControlChange(0, 64, 127))
        controller.onMidiEvent(MidiEvent.NoteOn(0, 48, 90))
        controller.onMidiEvent(MidiEvent.NoteOn(0, 72, 100))
        controller.onMidiEvent(MidiEvent.NoteOff(0, 48, 0))
        controller.onMidiEvent(MidiEvent.NoteOff(0, 72, 0))
        controller.onMidiEvent(MidiEvent.ControlChange(0, 64, 0))

        assertEquals(
            listOf(
                "cc RIGHT_1 64 127", "cc RIGHT_2 64 127", "cc LEFT_1 64 127",
                "on LEFT_1 48 90", "on RIGHT_1 72 100", "on RIGHT_2 72 100",
                "off LEFT_1 48", "off RIGHT_1 72", "off RIGHT_2 72",
                "cc RIGHT_1 64 0", "cc RIGHT_2 64 0", "cc LEFT_1 64 0",
            ),
            synth.events,
        )
    }

    @Test
    fun disconnectReleasesHeldNotesAndPedal() {
        val synth = RecordingSynth()
        val controller = controller(synth)
        controller.onMidiEvent(MidiEvent.ControlChange(0, 64, 127))
        controller.onMidiEvent(MidiEvent.NoteOn(0, 60, 100))
        synth.events.clear()

        controller.onConnectionLost()

        assertEquals(
            listOf(
                "off RIGHT_1 60",
                "cc RIGHT_1 64 0", "bend RIGHT_1 0",
                "cc RIGHT_2 64 0", "bend RIGHT_2 0",
                "cc LEFT_1 64 0", "bend LEFT_1 0",
            ),
            synth.events,
        )
    }

    @Test
    fun globalTransposeSurvivesPresetRecallAndReachesSynthAndEcho() = runBlocking {
        val synth = RecordingSynth()
        val repository = PresetRepository(temporaryFolder.root.resolve("presets.json"))
        repository.save(PerformancePreset(id = "saved", name = "Saved"))
        val controller = PerformanceController(
            synth, repository, SoundFontLibrary(temporaryFolder.root.resolve("soundfonts")),
            CoroutineScope(Dispatchers.Default),
        )
        val echo = mutableListOf<Triple<Int, Int, Int>>()
        controller.setEcho { status, data1, data2 -> echo += Triple(status, data1, data2) }
        controller.setGlobalTranspose(2)
        controller.recall("saved")
        synth.events.clear()
        echo.clear()

        controller.onMidiEvent(MidiEvent.NoteOn(0, 60, 100))
        controller.setGlobalTranspose(-2)
        controller.onMidiEvent(MidiEvent.NoteOff(0, 60, 0))

        assertEquals(listOf("on RIGHT_1 62 100", "off RIGHT_1 62"), synth.events)
        assertEquals(listOf(Triple(0x90, 62, 100), Triple(0x80, 62, 0)), echo)
    }

    private fun controller(synth: SynthEngine) = PerformanceController(
        synth,
        PresetRepository(temporaryFolder.root.resolve("presets.json")),
        SoundFontLibrary(temporaryFolder.root.resolve("soundfonts")),
        CoroutineScope(Dispatchers.Default),
    )

    private class RecordingSynth : SynthEngine {
        val events = mutableListOf<String>()

        override fun noteOn(slot: VoiceSlotId, note: Int, velocity: Int) { events += "on $slot $note $velocity" }
        override fun noteOff(slot: VoiceSlotId, note: Int) { events += "off $slot $note" }
        override fun controlChange(slot: VoiceSlotId, controller: Int, value: Int) { events += "cc $slot $controller $value" }
        override fun pitchBend(slot: VoiceSlotId, value: Int) { events += "bend $slot $value" }
        override fun allSoundsOff() = Unit
        override fun loadSoundFont(path: String): Int? = null
        override fun patches(soundFontId: Int): List<Patch> = emptyList()
        override fun unloadSoundFont(soundFontId: Int) = Unit
        override fun selectProgram(slot: VoiceSlotId, soundFontId: Int, bank: Int, program: Int) = true
        override fun setSlotVolume(slot: VoiceSlotId, volume: Float) = Unit
        override fun setReverb(level: Float) = Unit
        override fun setChorus(level: Float) = Unit
        override fun close() = Unit
    }
}
