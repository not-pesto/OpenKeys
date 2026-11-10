package io.github.openkeys.instruments

import io.github.openkeys.instruments.InstrumentCategory.Companion.of
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstrumentCategoryTest {

    @Test
    fun namesMapToSpecificCategories() {
        assertEquals(InstrumentCategory.GRAND_PIANO, of(0, 0, "Yamaha Grand Piano"))
        assertEquals(InstrumentCategory.ELECTRIC_PIANO, of(0, 4, "Rhodes EP"))
        assertEquals(InstrumentCategory.ELECTRIC_PIANO, of(0, 4, "E.Piano 1"))
        assertEquals(InstrumentCategory.VIOLIN, of(0, 40, "Violin"))
        assertEquals(InstrumentCategory.STRINGS, of(0, 48, "Strings"))
        assertEquals(InstrumentCategory.CHOIR, of(0, 52, "Choir Aahs"))
        assertEquals(InstrumentCategory.ELECTRIC_GUITAR, of(0, 27, "Clean Guitar"))
        assertEquals(InstrumentCategory.ACOUSTIC_GUITAR, of(0, 24, "Nylon String Guitar"))
        assertEquals(InstrumentCategory.BASS, of(0, 33, "Finger Bass"))
        assertEquals(InstrumentCategory.ORGAN, of(0, 17, "Percussive Organ"))
        assertEquals(InstrumentCategory.SYNTH_PAD, of(0, 89, "Warm Pad"))
    }

    @Test
    fun ambiguousWordsResolveInOrder() {
        assertEquals(InstrumentCategory.WOODWIND, of(0, 70, "Bassoon"))
        assertEquals(InstrumentCategory.STRINGS, of(0, 43, "Contrabass"))
        assertEquals(InstrumentCategory.BASS, of(0, 38, "Synth Bass 1"))
        assertEquals(InstrumentCategory.WOODWIND, of(0, 69, "English Horn"))
    }

    @Test
    fun unknownNameFallsBackToGmProgram() {
        assertEquals(InstrumentCategory.BASS, of(0, 35, "Fretless"))
        assertEquals(InstrumentCategory.BRASS, of(8, 56, "Patch 56"))
        assertEquals(InstrumentCategory.GRAND_PIANO, of(0, 0, null))
    }

    @Test
    fun drumBankIsDrums() {
        assertEquals(InstrumentCategory.DRUMS, of(128, 0, "Standard"))
    }

    @Test
    fun soundEffectsHaveNoImage() {
        assertNull(of(0, 122, "Seashore"))
    }
}
