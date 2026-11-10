package io.github.openkeys.ui

import androidx.annotation.DrawableRes
import io.github.openkeys.R
import io.github.openkeys.instruments.InstrumentCategory

@DrawableRes
fun InstrumentCategory.artRes(): Int = when (this) {
    InstrumentCategory.GRAND_PIANO -> R.drawable.instrument_grand_piano
    InstrumentCategory.ELECTRIC_PIANO -> R.drawable.instrument_electric_piano
    InstrumentCategory.MALLETS -> R.drawable.instrument_mallets
    InstrumentCategory.ORGAN -> R.drawable.instrument_organ
    InstrumentCategory.ACOUSTIC_GUITAR -> R.drawable.instrument_acoustic_guitar
    InstrumentCategory.ELECTRIC_GUITAR -> R.drawable.instrument_electric_guitar
    InstrumentCategory.BASS -> R.drawable.instrument_bass
    InstrumentCategory.VIOLIN -> R.drawable.instrument_violin
    InstrumentCategory.STRINGS -> R.drawable.instrument_strings
    InstrumentCategory.CHOIR -> R.drawable.instrument_choir
    InstrumentCategory.BRASS -> R.drawable.instrument_brass
    InstrumentCategory.SAX -> R.drawable.instrument_sax
    InstrumentCategory.WOODWIND -> R.drawable.instrument_woodwind
    InstrumentCategory.SYNTH_LEAD -> R.drawable.instrument_synth_lead
    InstrumentCategory.SYNTH_PAD -> R.drawable.instrument_synth_pad
    InstrumentCategory.DRUMS -> R.drawable.instrument_drums
}
