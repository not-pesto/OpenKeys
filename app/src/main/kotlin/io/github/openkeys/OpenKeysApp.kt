package io.github.openkeys

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import io.github.openkeys.audio.FluidSynthEngine
import io.github.openkeys.audio.SynthEngine
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.midi.MidiInputManager
import io.github.openkeys.routing.NoteRouter
import io.github.openkeys.routing.RoutedEvent
import io.github.openkeys.routing.VoiceSlotId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface SoundStatus {
    data class EngineFailed(val message: String) : SoundStatus
    data object NoSoundFont : SoundStatus
    data object Loading : SoundStatus
    data class Loaded(val name: String) : SoundStatus
    data class LoadFailed(val message: String) : SoundStatus
}

class OpenKeysApp : Application() {

    private val router = NoteRouter()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var synth: SynthEngine? = null

    private val _lastEvent = MutableStateFlow<MidiEvent?>(null)
    val lastEvent: StateFlow<MidiEvent?> = _lastEvent.asStateFlow()

    private val _sound = MutableStateFlow<SoundStatus>(SoundStatus.NoSoundFont)
    val sound: StateFlow<SoundStatus> = _sound.asStateFlow()

    lateinit var midi: MidiInputManager
        private set

    override fun onCreate() {
        super.onCreate()
        synth = runCatching { FluidSynthEngine.create(this) }.getOrNull()
        if (synth == null) _sound.value = SoundStatus.EngineFailed("Audio engine could not start")
        midi = MidiInputManager(this, ::onMidiEvent, ::onConnectionLost)
        midi.start()
    }

    /** Copies the picked SoundFont into app storage and loads it on every slot (bank 0, program 0). */
    fun loadSoundFont(uri: Uri) {
        val engine = synth ?: return
        _sound.value = SoundStatus.Loading
        io.launch {
            _sound.value = try {
                val name = displayName(uri)
                val dir = File(filesDir, "soundfonts").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.sf")
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                } ?: throw IOException("Cannot open file")
                val id = engine.loadSoundFont(file.absolutePath)
                if (id == null) {
                    file.delete()
                    SoundStatus.LoadFailed("$name is not a supported SoundFont (SF2/SF3)")
                } else {
                    VoiceSlotId.entries.forEach {
                        engine.selectProgram(it, id, bank = 0, program = 0)
                        engine.setSlotVolume(it, 0.8f)
                    }
                    engine.setReverb(0.3f)
                    SoundStatus.Loaded(name)
                }
            } catch (e: IOException) {
                SoundStatus.LoadFailed(e.message ?: "Could not read file")
            }
        }
    }

    fun panic() {
        dispatch(synchronized(router) { router.allNotesOff() })
        synth?.allSoundsOff()
    }

    private fun onMidiEvent(event: MidiEvent) {
        _lastEvent.value = event
        when (event) {
            is MidiEvent.NoteOn -> dispatch(synchronized(router) { router.noteOn(event.note, event.velocity) })
            is MidiEvent.NoteOff -> dispatch(synchronized(router) { router.noteOff(event.note) })
            is MidiEvent.ControlChange -> when (event.controller) {
                CC_ALL_SOUND_OFF, CC_ALL_NOTES_OFF -> panic()
                in PERFORMANCE_CCS -> VoiceSlotId.entries.forEach { synth?.controlChange(it, event.controller, event.value) }
            }
            is MidiEvent.PitchBend -> VoiceSlotId.entries.forEach { synth?.pitchBend(it, event.value) }
        }
    }

    private fun onConnectionLost() {
        dispatch(synchronized(router) { router.allNotesOff() })
        VoiceSlotId.entries.forEach {
            synth?.controlChange(it, CC_SUSTAIN, 0)
            synth?.pitchBend(it, 0)
        }
    }

    private fun dispatch(events: List<RoutedEvent>) {
        val engine = synth ?: return
        for (e in events) {
            when (e) {
                is RoutedEvent.NoteOn -> engine.noteOn(e.target.slot, e.target.note, e.velocity)
                is RoutedEvent.NoteOff -> engine.noteOff(e.target.slot, e.target.note)
            }
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "SoundFont"

    private companion object {
        const val CC_MODULATION = 1
        const val CC_EXPRESSION = 11
        const val CC_SUSTAIN = 64
        const val CC_SOSTENUTO = 66
        const val CC_SOFT = 67
        const val CC_ALL_SOUND_OFF = 120
        const val CC_ALL_NOTES_OFF = 123
        val PERFORMANCE_CCS = setOf(CC_MODULATION, CC_EXPRESSION, CC_SUSTAIN, CC_SOSTENUTO, CC_SOFT)
    }
}
