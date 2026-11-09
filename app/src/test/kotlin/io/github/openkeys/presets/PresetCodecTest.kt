package io.github.openkeys.presets

import io.github.openkeys.routing.SlotRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCodecTest {

    private val preset = PerformancePreset(
        id = "a",
        name = "Piano + Strings / Bass",
        splitEnabled = true,
        splitNote = 54,
        right1 = VoiceSlot(enabled = true, soundFontId = "sf1", program = 0, volume = 0.9f),
        right2 = VoiceSlot(enabled = true, soundFontId = "sf1", program = 48, volume = 0.5f, transposeSemitones = 12),
        left1 = VoiceSlot(enabled = true, soundFontId = "sf2", bank = 128, program = 32, transposeSemitones = -12),
        masterReverb = 0.4f,
        masterChorus = 0.1f,
    )

    @Test
    fun roundTripsAllSettings() {
        val decoded = PresetCodec.decode(PresetCodec.encode(listOf(preset)))
        assertEquals(listOf(preset), decoded.presets)
        assertTrue(decoded.rejected.isEmpty())
    }

    @Test
    fun splitIsOptionalPerPreset() {
        val noSplit = preset.copy(id = "b", splitEnabled = false)
        val decoded = PresetCodec.decode(PresetCodec.encode(listOf(preset, noSplit))).presets
        assertEquals(listOf(true, false), decoded.map { it.splitEnabled })
    }

    @Test
    fun mapsToRoutingConfig() {
        val config = preset.toRoutingConfig()
        assertEquals(54, config.splitNote)
        assertEquals(SlotRouting(enabled = true, transposeSemitones = 12), config.right2)
        assertEquals(SlotRouting(enabled = true, transposeSemitones = -12), config.left1)
    }

    @Test
    fun missingFieldsUseDefaultsAndUnknownKeysAreIgnored() {
        val decoded = PresetCodec.decode(
            """{"schemaVersion":1,"futureField":true,"presets":[{"id":"x","name":"Minimal","extra":1}]}""",
        )
        assertEquals(PerformancePreset(id = "x", name = "Minimal"), decoded.presets.single())
    }

    @Test
    fun invalidAndDuplicatePresetsAreRejectedNotFatal() {
        val bad = preset.copy(id = "c", name = "Bad", right1 = preset.right1.copy(program = 200))
        val dup = preset.copy(name = "Dup")
        val decoded = PresetCodec.decode(PresetCodec.encode(listOf(preset, bad, dup)))
        assertEquals(listOf(preset), decoded.presets)
        assertEquals(2, decoded.rejected.size)
        assertTrue(decoded.rejected[0].contains("Right 1: program 200"))
    }

    @Test(expected = PresetFormatException::class)
    fun rejectsNewerSchema() {
        PresetCodec.decode("""{"schemaVersion":99,"presets":[]}""")
    }

    @Test(expected = PresetFormatException::class)
    fun rejectsCorruptFile() {
        PresetCodec.decode("""{"schemaVersion":1,"presets":[{"id":""")
    }

    @Test
    fun reportsReferencedSoundFonts() {
        assertEquals(setOf("sf1", "sf2"), preset.soundFontIds())
    }
}
