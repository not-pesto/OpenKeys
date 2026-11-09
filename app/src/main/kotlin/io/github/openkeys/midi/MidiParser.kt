package io.github.openkeys.midi

sealed interface MidiEvent {
    val channel: Int

    data class NoteOn(override val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    data class NoteOff(override val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    data class ControlChange(override val channel: Int, val controller: Int, val value: Int) : MidiEvent

    /** [value] is centered at 0, range -8192..8191. */
    data class PitchBend(override val channel: Int, val value: Int) : MidiEvent
}

/**
 * Stateful MIDI 1.0 byte-stream parser: handles running status, interleaved real-time bytes,
 * and skips SysEx and system-common messages. Not thread-safe.
 */
class MidiParser(private val onEvent: (MidiEvent) -> Unit) {

    private var status = 0
    private var inSysEx = false
    private val data = IntArray(2)
    private var dataCount = 0

    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
        for (i in offset until offset + count) feedByte(bytes[i].toInt() and 0xFF)
    }

    private fun feedByte(b: Int) {
        when {
            b >= 0xF8 -> return
            b == 0xF0 -> { inSysEx = true; status = 0 }
            b == 0xF7 -> inSysEx = false
            b >= 0x80 -> {
                inSysEx = false
                dataCount = 0
                status = if (dataLength(b) == 0) 0 else b
            }
            inSysEx || status == 0 -> return
            else -> {
                data[dataCount++] = b
                if (dataCount == dataLength(status)) {
                    dataCount = 0
                    if (status < 0xF0) dispatch() else status = 0
                }
            }
        }
    }

    private fun dispatch() {
        val channel = status and 0x0F
        when (status and 0xF0) {
            0x80 -> onEvent(MidiEvent.NoteOff(channel, data[0], data[1]))
            0x90 -> onEvent(
                if (data[1] == 0) MidiEvent.NoteOff(channel, data[0], 0)
                else MidiEvent.NoteOn(channel, data[0], data[1]),
            )
            0xB0 -> onEvent(MidiEvent.ControlChange(channel, data[0], data[1]))
            0xE0 -> onEvent(MidiEvent.PitchBend(channel, ((data[1] shl 7) or data[0]) - 8192))
        }
    }

    private fun dataLength(status: Int): Int = when {
        status < 0xF0 -> if (status and 0xF0 == 0xC0 || status and 0xF0 == 0xD0) 1 else 2
        status == 0xF1 || status == 0xF3 -> 1
        status == 0xF2 -> 2
        else -> 0
    }
}
