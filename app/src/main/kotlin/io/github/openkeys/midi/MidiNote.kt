package io.github.openkeys.midi

/** MIDI note numbers 0..127; middle C (60) is "C4". */
object MidiNote {
    const val MIN = 0
    const val MAX = 127
    const val MIDDLE_C = 60

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(note: Int): String {
        require(note in MIN..MAX) { "MIDI note out of range: $note" }
        return NAMES[note % 12] + (note / 12 - 1)
    }
}
