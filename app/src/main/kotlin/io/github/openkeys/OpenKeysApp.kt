package io.github.openkeys

import android.app.Application
import android.content.Context
import io.github.openkeys.audio.FluidSynthEngine
import io.github.openkeys.midi.KshojiMidiInput
import io.github.openkeys.midi.MidiDriverType
import io.github.openkeys.midi.MidiInput
import io.github.openkeys.midi.MidiInputManager
import io.github.openkeys.presets.PresetRepository
import io.github.openkeys.soundfonts.SoundFontLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class OpenKeysApp : Application() {

    lateinit var performance: PerformanceController
        private set

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    private val _midiDriver = MutableStateFlow(MidiDriverType.ANDROID_MIDI)
    val midiDriver: StateFlow<MidiDriverType> = _midiDriver.asStateFlow()

    private lateinit var _midi: MutableStateFlow<MidiInput>
    val midi: StateFlow<MidiInput> get() = _midi

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val synth = runCatching { FluidSynthEngine.create(this) }.getOrNull()
        performance = PerformanceController(
            synth = synth,
            presets = PresetRepository(File(filesDir, "presets/presets.json")),
            soundFonts = SoundFontLibrary(File(filesDir, "soundfonts")),
            scope = scope,
        )
        performance.start()

        val driver = prefs.getString(KEY_MIDI_DRIVER, null)
            ?.let { runCatching { MidiDriverType.valueOf(it) }.getOrNull() }
            ?: MidiDriverType.ANDROID_MIDI
        _midiDriver.value = driver
        _midi = MutableStateFlow(createMidi(driver).also { it.start() })
    }

    fun setMidiDriver(type: MidiDriverType) {
        if (type == _midiDriver.value) return
        prefs.edit().putString(KEY_MIDI_DRIVER, type.name).apply()
        _midi.value.release()
        performance.onConnectionLost()
        _midiDriver.value = type
        _midi.value = createMidi(type).also { it.start() }
    }

    private fun createMidi(type: MidiDriverType): MidiInput = when (type) {
        MidiDriverType.ANDROID_MIDI -> MidiInputManager(this, performance::onMidiEvent, performance::onConnectionLost)
        MidiDriverType.USB_MIDI_DRIVER -> KshojiMidiInput(this, performance::onMidiEvent, performance::onConnectionLost)
    }

    private companion object {
        const val KEY_MIDI_DRIVER = "midi_driver"
    }
}
