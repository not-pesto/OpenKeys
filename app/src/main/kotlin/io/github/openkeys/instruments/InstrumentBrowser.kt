package io.github.openkeys.instruments

import io.github.openkeys.audio.Patch
import io.github.openkeys.presets.VoiceSlot
import io.github.openkeys.soundfonts.SoundFontEntry

enum class VoiceCategory(val title: String) {
    PIANO("Piano & E.Piano"),
    ORGAN("Organ"),
    GUITAR("Guitar"),
    BASS("Bass"),
    ACCORDION("Accordion"),
    STRINGS("Strings"),
    BRASS("Brass"),
    WOODWIND("Woodwind"),
    CHOIR_PAD("Choir & Pad"),
    SYNTH("Synth"),
    PERCUSSION("Perc. & Drums"),
    USER("Expansion / User");

    companion object {
        fun of(patch: Patch): VoiceCategory {
            val family = InstrumentCategory.of(patch.bank, patch.program, patch.name)
            if (patch.bank != InstrumentCategory.DRUM_BANK &&
                (patch.name.contains("accordion", ignoreCase = true) ||
                    patch.name.contains("accordian", ignoreCase = true) ||
                    patch.name.contains("bandoneon", ignoreCase = true) ||
                    (family == InstrumentCategory.ORGAN && (patch.program == 21 || patch.program == 23)))
            ) return ACCORDION
            return when (family) {
                InstrumentCategory.GRAND_PIANO, InstrumentCategory.ELECTRIC_PIANO -> PIANO
                InstrumentCategory.ORGAN -> ORGAN
                InstrumentCategory.ACOUSTIC_GUITAR, InstrumentCategory.ELECTRIC_GUITAR -> GUITAR
                InstrumentCategory.BASS -> BASS
                InstrumentCategory.VIOLIN, InstrumentCategory.STRINGS -> STRINGS
                InstrumentCategory.BRASS -> BRASS
                InstrumentCategory.WOODWIND, InstrumentCategory.SAX -> WOODWIND
                InstrumentCategory.CHOIR -> CHOIR_PAD
                InstrumentCategory.SYNTH_LEAD, InstrumentCategory.SYNTH_PAD -> SYNTH
                InstrumentCategory.DRUMS, InstrumentCategory.MALLETS -> PERCUSSION
                null -> USER
            }
        }
    }
}

data class BrowserInstrument(val soundFont: SoundFontEntry, val patch: Patch) {
    val name: String get() = soundFont.instrumentName(patch.bank, patch.program, patch.name)
    // Renaming a patch does not move it to a different family.
    val category: VoiceCategory get() = VoiceCategory.of(patch)
    val key: String get() = "${soundFont.id}:${patch.bank}:${patch.program}"

    fun isAssignedTo(slot: VoiceSlot): Boolean =
        soundFont.id == slot.soundFontId && patch.bank == slot.bank && patch.program == slot.program
}

fun instrumentCatalog(
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
): List<BrowserInstrument> = soundFonts.flatMap { sf ->
    patches[sf.id].orEmpty().map { BrowserInstrument(sf, it) }
}

fun List<BrowserInstrument>.inCategory(category: VoiceCategory, search: String): List<BrowserInstrument> {
    val query = search.trim()
    return filter {
        it.category == category &&
            (it.name.contains(query, ignoreCase = true) || it.soundFont.sourceFileName.contains(query, ignoreCase = true))
    }
}
