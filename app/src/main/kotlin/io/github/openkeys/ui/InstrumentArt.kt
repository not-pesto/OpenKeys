package io.github.openkeys.ui

import androidx.annotation.DrawableRes
import io.github.openkeys.R
import io.github.openkeys.instruments.InstrumentCategory

/** Tile artwork per category. [lineArt] images are tinted to the theme; photos are drawn as-is. */
data class InstrumentArt(@param:DrawableRes val res: Int, val lineArt: Boolean = true)

fun InstrumentCategory.art(): InstrumentArt = when (this) {
    InstrumentCategory.GRAND_PIANO -> InstrumentArt(R.drawable.instrument_grand_piano)
    InstrumentCategory.ELECTRIC_PIANO -> InstrumentArt(R.drawable.instrument_electric_piano)
    InstrumentCategory.MALLETS -> InstrumentArt(R.drawable.instrument_mallets)
    InstrumentCategory.ORGAN -> InstrumentArt(R.drawable.instrument_organ)
    InstrumentCategory.ACOUSTIC_GUITAR -> InstrumentArt(R.drawable.instrument_acoustic_guitar)
    InstrumentCategory.ELECTRIC_GUITAR -> InstrumentArt(R.drawable.instrument_electric_guitar)
    InstrumentCategory.BASS -> InstrumentArt(R.drawable.instrument_bass)
    InstrumentCategory.VIOLIN -> InstrumentArt(R.drawable.instrument_violin)
    InstrumentCategory.STRINGS -> InstrumentArt(R.drawable.instrument_strings)
    InstrumentCategory.CHOIR -> InstrumentArt(R.drawable.instrument_choir)
    InstrumentCategory.BRASS -> InstrumentArt(R.drawable.instrument_brass)
    InstrumentCategory.SAX -> InstrumentArt(R.drawable.instrument_sax)
    InstrumentCategory.WOODWIND -> InstrumentArt(R.drawable.instrument_woodwind)
    InstrumentCategory.SYNTH_LEAD, InstrumentCategory.SYNTH_PAD -> InstrumentArt(R.drawable.instrument_synth)
    InstrumentCategory.DRUMS -> InstrumentArt(R.drawable.instrument_drums)
}
