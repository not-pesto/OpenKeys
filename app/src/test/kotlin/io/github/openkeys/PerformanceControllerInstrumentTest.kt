package io.github.openkeys

import io.github.openkeys.audio.Patch
import io.github.openkeys.audio.SynthEngine
import io.github.openkeys.presets.PresetRepository
import io.github.openkeys.routing.VoiceSlotId
import io.github.openkeys.soundfonts.SoundFontEntry
import io.github.openkeys.soundfonts.SoundFontLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PerformanceControllerInstrumentTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun loadsAllImportedFontsAndSelectsExactPatchWithoutChangingOtherSlotSettings() = runBlocking {
        val directory = temporaryFolder.root.resolve("soundfonts")
        val library = SoundFontLibrary(directory)
        library.newFile("first").writeText("first")
        library.newFile("second").writeText("second")
        library.add(SoundFontEntry("first", "First", "first.sf"))
        library.add(SoundFontEntry("second", "Second", "second.sf"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val synth = CatalogSynth()
            val controller = PerformanceController(
                synth,
                PresetRepository(temporaryFolder.root.resolve("presets.json")),
                SoundFontLibrary(directory),
                scope,
            )
            controller.start()
            val catalog = withTimeout(5000) { controller.patches.first { it.size == 2 } }
            assertEquals(listOf(Patch(2, 10, "Piano")), catalog["first"])
            assertEquals(listOf(Patch(5, 10, "Piano")), catalog["second"])

            for (id in VoiceSlotId.entries) {
                controller.updateSlot(id) { it.copy(enabled = true, volume = 0.4f, transposeSemitones = 12) }
                val before = controller.current.value
                controller.selectInstrument(id, "second", catalog.getValue("second").single())
                val after = controller.current.value
                assertEquals(
                    before.slot(id).copy(soundFontId = "second", bank = 5, program = 10),
                    after.slot(id),
                )
                VoiceSlotId.entries.filter { it != id }.forEach {
                    assertEquals(before.slot(it), after.slot(it))
                }
                withTimeout(5000) {
                    while (synth.selections[id] != Triple(2, 5, 10)) yield()
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun reportsSoundFontsThatCannotBeLoaded() = runBlocking {
        val directory = temporaryFolder.root.resolve("soundfonts")
        val library = SoundFontLibrary(directory)
        library.newFile("broken").writeText("broken")
        library.add(SoundFontEntry("broken", "Broken", "broken.sf"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = PerformanceController(
                CatalogSynth(),
                PresetRepository(temporaryFolder.root.resolve("presets.json")),
                SoundFontLibrary(directory),
                scope,
            )
            controller.start()
            assertEquals(
                "Could not load instruments from: Broken",
                withTimeout(5000) { controller.message.first { it != null } },
            )
        } finally {
            scope.cancel()
        }
    }

    private class CatalogSynth : SynthEngine {
        val selections = java.util.concurrent.ConcurrentHashMap<VoiceSlotId, Triple<Int, Int, Int>>()
        override fun loadSoundFont(path: String): Int? = when {
            path.endsWith("first.sf") -> 1
            path.endsWith("second.sf") -> 2
            else -> null
        }
        override fun patches(soundFontId: Int) = listOf(Patch(if (soundFontId == 1) 2 else 5, 10, "Piano"))
        override fun unloadSoundFont(soundFontId: Int) = Unit
        override fun selectProgram(slot: VoiceSlotId, soundFontId: Int, bank: Int, program: Int): Boolean {
            selections[slot] = Triple(soundFontId, bank, program)
            return true
        }
        override fun setSlotVolume(slot: VoiceSlotId, volume: Float) = Unit
        override fun setReverb(level: Float) = Unit
        override fun setChorus(level: Float) = Unit
        override fun noteOn(slot: VoiceSlotId, note: Int, velocity: Int) = Unit
        override fun noteOff(slot: VoiceSlotId, note: Int) = Unit
        override fun controlChange(slot: VoiceSlotId, controller: Int, value: Int) = Unit
        override fun pitchBend(slot: VoiceSlotId, value: Int) = Unit
        override fun allSoundsOff() = Unit
        override fun close() = Unit
    }
}
