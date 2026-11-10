package io.github.openkeys.routing

import io.github.openkeys.audio.channel
import io.github.openkeys.presets.PerformancePreset
import kotlin.math.roundToInt

/** Sends routed performance data to an external MIDI device, using the same channel per slot as the synth. */
class MidiEcho(private val send: (status: Int, data1: Int, data2: Int) -> Unit) {

    private var sentSetup: PerformancePreset? = null

    fun routed(events: List<RoutedEvent>) {
        for (e in events) {
            val ch = e.target.slot.channel()
            when (e) {
                is RoutedEvent.NoteOn -> send(NOTE_ON or ch, e.target.note, e.velocity)
                is RoutedEvent.NoteOff -> send(NOTE_OFF or ch, e.target.note, 0)
            }
        }
    }

    fun controlChange(controller: Int, value: Int) =
        VoiceSlotId.entries.forEach { send(CONTROL_CHANGE or it.channel(), controller, value) }

    /** [value] is centred at 0 (-8192..8191). */
    fun pitchBend(value: Int) {
        val v = (value + 8192).coerceIn(0, 16383)
        VoiceSlotId.entries.forEach { send(PITCH_BEND or it.channel(), v and 0x7F, v shr 7) }
    }

    /** Sends program and volume for slots that changed since the last call. */
    @Synchronized
    fun setup(preset: PerformancePreset, force: Boolean = false) {
        val prev = if (force) null else sentSetup
        for (id in VoiceSlotId.entries) {
            val slot = preset.slot(id)
            val old = prev?.slot(id)
            // Drum-bank programs are kits, not GM melodic voices.
            if (slot.bank != DRUM_BANK && (old == null || old.program != slot.program || old.bank != slot.bank)) {
                send(PROGRAM_CHANGE or id.channel(), slot.program, 0)
            }
            if (old == null || old.volume != slot.volume) {
                send(CONTROL_CHANGE or id.channel(), CC_VOLUME, (slot.volume * 127).roundToInt())
            }
        }
        sentSetup = preset
    }

    fun silence() {
        controlChange(CC_SUSTAIN, 0)
        controlChange(CC_ALL_NOTES_OFF, 0)
    }

    companion object {
        const val NOTE_OFF = 0x80
        const val NOTE_ON = 0x90
        const val CONTROL_CHANGE = 0xB0
        const val PROGRAM_CHANGE = 0xC0
        const val PITCH_BEND = 0xE0
        private const val CC_VOLUME = 7
        private const val CC_SUSTAIN = 64
        private const val CC_ALL_NOTES_OFF = 123
        private const val DRUM_BANK = 128
    }
}
