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

    private val _echoToKeyboard = MutableStateFlow(false)
    val echoToKeyboard: StateFlow<Boolean> = _echoToKeyboard.asStateFlow()

    private val _globalTranspose = MutableStateFlow(0)
    val globalTranspose: StateFlow<Int> = _globalTranspose.asStateFlow()

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
        val transpose = prefs.getInt(KEY_GLOBAL_TRANSPOSE, 0).coerceIn(-12, 12)
        _globalTranspose.value = transpose
        performance.setGlobalTranspose(transpose)
        performance.start()

        val driver = prefs.getString(KEY_MIDI_DRIVER, null)
            ?.let { runCatching { MidiDriverType.valueOf(it) }.getOrNull() }
            ?: MidiDriverType.ANDROID_MIDI
        _midiDriver.value = driver
        _midi = MutableStateFlow(createMidi(driver).also { it.start() })
        if (prefs.getBoolean(KEY_ECHO, false)) setEchoToKeyboard(true)
    }

    fun setEchoToKeyboard(on: Boolean) {
        prefs.edit().putBoolean(KEY_ECHO, on).apply()
        _echoToKeyboard.value = on
        performance.setEcho(if (on) { s, d1, d2 -> _midi.value.send(s, d1, d2) } else null)
    }

    fun setGlobalTranspose(semitones: Int) {
        require(semitones in -12..12)
        performance.setGlobalTranspose(semitones)
        _globalTranspose.value = semitones
        prefs.edit().putInt(KEY_GLOBAL_TRANSPOSE, semitones).apply()
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
        MidiDriverType.ANDROID_MIDI ->
            MidiInputManager(this, performance::onMidiEvent, performance::onConnectionLost, performance::resendEchoSetup)
        MidiDriverType.USB_MIDI_DRIVER ->
            KshojiMidiInput(this, performance::onMidiEvent, performance::onConnectionLost, performance::resendEchoSetup)
    }

    private companion object {
        const val KEY_MIDI_DRIVER = "midi_driver"
        const val KEY_ECHO = "echo_to_keyboard"
        const val KEY_GLOBAL_TRANSPOSE = "global_transpose"
    }
}
