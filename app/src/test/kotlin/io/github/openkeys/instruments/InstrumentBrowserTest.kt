package io.github.openkeys.instruments

import io.github.openkeys.audio.Patch
import io.github.openkeys.presets.VoiceSlot
import io.github.openkeys.soundfonts.SoundFontEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentBrowserTest {
    @Test
    fun groupsMetadataWithoutDuplicatingImportedInstruments() {
        val patches = listOf(
            Patch(0, 0, "Grand"), Patch(0, 4, "Rhodes"),
            Patch(0, 17, "Organ"), Patch(0, 24, "Nylon Guitar"),
            Patch(0, 27, "Electric Guitar"), Patch(0, 33, "Bass"),
            Patch(0, 21, "Patch 21"), Patch(0, 23, "Bandoneon"),
            Patch(0, 40, "Violin"), Patch(0, 48, "Strings"),
            Patch(0, 56, "Trumpet"), Patch(0, 65, "Sax"), Patch(0, 73, "Flute"),
            Patch(0, 52, "Choir"), Patch(0, 89, "Warm Pad"), Patch(0, 80, "Lead"),
            Patch(128, 0, "Standard"), Patch(0, 12, "Marimba"), Patch(0, 122, "Seashore"),
        )
        val sf = SoundFontEntry("sf", "User Library", "sf.sf")
        val catalog = instrumentCatalog(listOf(sf), mapOf("sf" to patches))
        assertEquals(
            listOf(
                VoiceCategory.PIANO, VoiceCategory.PIANO, VoiceCategory.ORGAN,
                VoiceCategory.GUITAR, VoiceCategory.GUITAR, VoiceCategory.BASS,
                VoiceCategory.ACCORDION, VoiceCategory.ACCORDION, VoiceCategory.STRINGS,
                VoiceCategory.STRINGS, VoiceCategory.BRASS, VoiceCategory.WOODWIND,
                VoiceCategory.WOODWIND, VoiceCategory.CHOIR_PAD, VoiceCategory.SYNTH,
                VoiceCategory.SYNTH, VoiceCategory.PERCUSSION, VoiceCategory.PERCUSSION, VoiceCategory.USER,
            ),
            catalog.map { it.category },
        )
        assertEquals(catalog.size, VoiceCategory.entries.sumOf { catalog.inCategory(it, "").size })
        assertEquals(VoiceCategory.PERCUSSION, VoiceCategory.of(Patch(128, 21, "Accordion Kit")))
        assertEquals(VoiceCategory.PIANO, VoiceCategory.of(Patch(0, 21, "Grand Piano")))
    }

    @Test
    fun searchesDisplayNameAndSourceFilenameOnlyWithinSelectedCategory() {
        val first = SoundFontEntry(
            "first", "Renamed Library", "uuid.sf",
            instrumentNames = mapOf("0:0" to "Stage Grand"),
            sourceFileName = "Steinway.sf2",
        )
        val second = SoundFontEntry("second", "Other.sf3", "other.sf")
        val catalog = instrumentCatalog(
            listOf(first, second),
            mapOf(
                "first" to listOf(Patch(0, 0, "Piano"), Patch(0, 33, "Bass")),
                "second" to listOf(Patch(0, 4, "Rhodes")),
                "removed" to listOf(Patch(0, 0, "Missing")),
            ),
        )
        assertEquals(listOf("Stage Grand"), catalog.inCategory(VoiceCategory.PIANO, " STAGE ").map { it.name })
        assertEquals(listOf("Stage Grand"), catalog.inCategory(VoiceCategory.PIANO, "STEINWAY.SF2").map { it.name })
        assertEquals(listOf("Bass"), catalog.inCategory(VoiceCategory.BASS, "steinway").map { it.name })
        assertTrue(catalog.inCategory(VoiceCategory.PIANO, "bass").isEmpty())
        assertTrue(catalog.inCategory(VoiceCategory.PIANO, "uuid").isEmpty())
        assertEquals(2, catalog.inCategory(VoiceCategory.PIANO, "").size)
    }

    @Test
    fun assignmentHighlightUsesSoundFontBankAndProgramNotInstrumentName() {
        val first = SoundFontEntry("first", "First.sf2", "first.sf")
        val second = SoundFontEntry("second", "Second.sf2", "second.sf")
        val assigned = VoiceSlot(soundFontId = "first", bank = 2, program = 10)
        assertTrue(BrowserInstrument(first, Patch(2, 10, "Grand")).isAssignedTo(assigned))
        assertFalse(BrowserInstrument(first, Patch(0, 10, "Grand")).isAssignedTo(assigned))
        assertFalse(BrowserInstrument(second, Patch(2, 10, "Grand")).isAssignedTo(assigned))
    }
}
