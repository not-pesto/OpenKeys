package io.github.openkeys.audio

import android.content.Context
import android.media.AudioManager
import io.github.openkeys.routing.VoiceSlotId

class FluidSynthEngine private constructor(private var handle: Long) : SynthEngine {

    override fun noteOn(slot: VoiceSlotId, note: Int, velocity: Int) = nativeNoteOn(live(), slot.channel(), note, velocity)

    override fun noteOff(slot: VoiceSlotId, note: Int) = nativeNoteOff(live(), slot.channel(), note)

    override fun controlChange(slot: VoiceSlotId, controller: Int, value: Int) =
        nativeControlChange(live(), slot.channel(), controller, value)

    override fun pitchBend(slot: VoiceSlotId, value: Int) =
        nativePitchBend(live(), slot.channel(), (value + 8192).coerceIn(0, 16383))

    override fun allSoundsOff() = nativeAllSoundsOff(live())

    override fun loadSoundFont(path: String): Int? = nativeLoadSoundFont(live(), path).takeIf { it >= 0 }

    override fun patches(soundFontId: Int): List<Patch> {
        val numbers = nativePatchNumbers(live(), soundFontId)
        val names = nativePatchNames(live(), soundFontId)
        return names.indices.map { Patch(numbers[2 * it], numbers[2 * it + 1], names[it]) }
            .sortedWith(compareBy({ it.bank }, { it.program }))
    }

    override fun selectProgram(slot: VoiceSlotId, soundFontId: Int, bank: Int, program: Int): Boolean =
        nativeProgramSelect(live(), slot.channel(), soundFontId, bank, program)

    override fun setSlotVolume(slot: VoiceSlotId, volume: Float) =
        controlChange(slot, CC_VOLUME, (volume.coerceIn(0f, 1f) * 127).toInt())

    override fun setReverb(level: Float) = nativeSetReverb(live(), level.coerceIn(0f, 1f).toDouble())

    override fun setChorus(level: Float) = nativeSetChorus(live(), level.coerceIn(0f, 1f).toDouble())

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private fun live(): Long = handle.also { check(it != 0L) { "Synth engine is closed" } }

    companion object {
        private const val CC_VOLUME = 7

        init {
            System.loadLibrary("openkeys_synth")
        }

        /** Starts FluidSynth with its Oboe output at the device's native rate; null if audio cannot start. */
        fun create(context: Context): FluidSynthEngine? {
            val audio = context.getSystemService(AudioManager::class.java)
            val sampleRate = audio?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            val handle = nativeCreate(sampleRate)
            return if (handle == 0L) null else FluidSynthEngine(handle)
        }

        @JvmStatic private external fun nativeCreate(sampleRate: Int): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeLoadSoundFont(handle: Long, path: String): Int
        @JvmStatic private external fun nativePatchNumbers(handle: Long, sfontId: Int): IntArray
        @JvmStatic private external fun nativePatchNames(handle: Long, sfontId: Int): Array<String>
        @JvmStatic private external fun nativeProgramSelect(handle: Long, chan: Int, sfontId: Int, bank: Int, program: Int): Boolean
        @JvmStatic private external fun nativeNoteOn(handle: Long, chan: Int, key: Int, vel: Int)
        @JvmStatic private external fun nativeNoteOff(handle: Long, chan: Int, key: Int)
        @JvmStatic private external fun nativeControlChange(handle: Long, chan: Int, ctrl: Int, value: Int)
        @JvmStatic private external fun nativePitchBend(handle: Long, chan: Int, value: Int)
        @JvmStatic private external fun nativeAllSoundsOff(handle: Long)
        @JvmStatic private external fun nativeSetReverb(handle: Long, level: Double)
        @JvmStatic private external fun nativeSetChorus(handle: Long, level: Double)
    }
}
