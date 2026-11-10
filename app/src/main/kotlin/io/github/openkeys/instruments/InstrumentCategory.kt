package io.github.openkeys.instruments

enum class InstrumentCategory {
    GRAND_PIANO, ELECTRIC_PIANO, MALLETS, ORGAN,
    ACOUSTIC_GUITAR, ELECTRIC_GUITAR, BASS,
    VIOLIN, STRINGS, CHOIR, BRASS, SAX, WOODWIND,
    SYNTH_LEAD, SYNTH_PAD, DRUMS;

    companion object {
        const val DRUM_BANK = 128

        /** Patch name keywords win; otherwise falls back to the General MIDI program family. */
        fun of(bank: Int, program: Int, name: String?): InstrumentCategory? {
            if (bank == DRUM_BANK) return DRUMS
            return name?.let(::fromName) ?: fromGmProgram(program)
        }

        private val nameRules: List<Pair<List<String>, InstrumentCategory>> = listOf(
            listOf("choir", "voice", "vox", "aah", "ooh") to CHOIR,
            listOf("e.piano", "e. piano", "epiano", "e-piano", "electric piano", "elec piano", "rhodes", "wurli", "clav", "dx ep") to ELECTRIC_PIANO,
            listOf("harpsi", "piano", "grand") to GRAND_PIANO,
            listOf("organ", "hammond", "church") to ORGAN,
            listOf("bassoon", "english horn", "flute", "piccolo", "recorder", "pan pipe", "panflute", "oboe", "clarinet", "whistle", "ocarina", "shakuhachi") to WOODWIND,
            listOf("nylon", "steel g", "steel str", "acoustic g", "acou gt", "folk", "classical g", "12 str", "banjo") to ACOUSTIC_GUITAR,
            listOf("contrabass", "cello", "string", "orchestra", "pizz", "tremolo") to STRINGS,
            listOf("violin", "viola", "fiddle") to VIOLIN,
            listOf("bass") to BASS,
            listOf("guitar", "gtr", "gt.", "distort", "overdrive", "feedback") to ELECTRIC_GUITAR,
            listOf("sax") to SAX,
            listOf("trumpet", "trombone", "horn", "brass", "tuba", "cornet", "flugel") to BRASS,
            listOf("pad", "sweep", "atmosph", "halo") to SYNTH_PAD,
            listOf("synth", "lead", "saw", "square") to SYNTH_LEAD,
            listOf("vibra", "marimba", "xylo", "bell", "celesta", "glock", "music box", "tubular", "kalimba") to MALLETS,
            listOf("drum", "kit", "perc", "timpani") to DRUMS,
        )

        private fun fromName(name: String): InstrumentCategory? {
            val n = name.lowercase()
            return nameRules.firstOrNull { (keys, _) -> keys.any(n::contains) }?.second
        }

        private fun fromGmProgram(program: Int): InstrumentCategory? = when (program) {
            in 0..3, 6 -> GRAND_PIANO
            4, 5, 7 -> ELECTRIC_PIANO
            in 8..15 -> MALLETS
            in 16..23 -> ORGAN
            24, 25 -> ACOUSTIC_GUITAR
            in 26..31 -> ELECTRIC_GUITAR
            in 32..39 -> BASS
            40, 41, 110 -> VIOLIN
            in 42..45, in 48..51, 55 -> STRINGS
            47, in 112..119 -> DRUMS
            in 52..54 -> CHOIR
            in 56..63 -> BRASS
            in 64..67 -> SAX
            in 68..79 -> WOODWIND
            in 80..87 -> SYNTH_LEAD
            in 88..103 -> SYNTH_PAD
            else -> null
        }
    }
}
