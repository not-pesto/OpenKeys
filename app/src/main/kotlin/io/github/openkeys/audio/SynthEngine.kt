package io.github.openkeys.audio

import io.github.openkeys.routing.VoiceSlotId

data class Patch(val bank: Int, val program: Int, val name: String)

/** Engine-neutral synth API so presets/UI/routing do not depend on FluidSynth. */
interface SynthEngine : AutoCloseable {
    fun noteOn(slot: VoiceSlotId, note: Int, velocity: Int)
    fun noteOff(slot: VoiceSlotId, note: Int)
    fun controlChange(slot: VoiceSlotId, controller: Int, value: Int)

    /** [value] is centered at 0, range -8192..8191. */
    fun pitchBend(slot: VoiceSlotId, value: Int)
    fun allSoundsOff()

    /** Blocking; call off the UI and MIDI threads. Returns an engine id, or null on failure. */
    fun loadSoundFont(path: String): Int?
    fun patches(soundFontId: Int): List<Patch>
    fun unloadSoundFont(soundFontId: Int)
    fun selectProgram(slot: VoiceSlotId, soundFontId: Int, bank: Int, program: Int): Boolean
    fun setSlotVolume(slot: VoiceSlotId, volume: Float)
    fun setReverb(level: Float)
    fun setChorus(level: Float)
}

/** Fixed MIDI channel per slot; channel 9 (GM drums) is avoided. */
fun VoiceSlotId.channel(): Int = when (this) {
    VoiceSlotId.RIGHT_1 -> 0
    VoiceSlotId.RIGHT_2 -> 1
    VoiceSlotId.LEFT_1 -> 2
}
