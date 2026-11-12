package io.github.openkeys

import io.github.openkeys.audio.Patch
import io.github.openkeys.audio.SynthEngine
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.presets.PerformancePreset
import io.github.openkeys.presets.PresetFormatException
import io.github.openkeys.presets.PresetRepository
import io.github.openkeys.presets.VoiceSlot
import io.github.openkeys.routing.MidiEcho
import io.github.openkeys.routing.NoteRouter
import io.github.openkeys.routing.RoutedEvent
import io.github.openkeys.routing.VoiceSlotId
import io.github.openkeys.soundfonts.SoundFontEntry
import io.github.openkeys.soundfonts.SoundFontLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the live performance state: the working preset, MIDI → router → synth dispatch,
 * SoundFont loading, and preset persistence. Engine configuration runs on one background
 * thread; MIDI dispatch runs on the MIDI driver's thread.
 */
class PerformanceController(
    private val synth: SynthEngine?,
    private val presets: PresetRepository,
    private val soundFonts: SoundFontLibrary,
    private val scope: CoroutineScope,
) {
    private val router = NoteRouter()
    private val engineThread = Dispatchers.IO.limitedParallelism(1)

    private val _current = MutableStateFlow(PerformancePreset(id = newId(), name = "New preset"))
    val current: StateFlow<PerformancePreset> = _current.asStateFlow()
    val presetList: StateFlow<List<PerformancePreset>> = presets.presets
    val soundFontList: StateFlow<List<SoundFontEntry>> = soundFonts.entries

    private val _patches = MutableStateFlow<Map<String, List<Patch>>>(emptyMap())
    val patches: StateFlow<Map<String, List<Patch>>> = _patches.asStateFlow()

    private val _message = MutableStateFlow<String?>(if (synth == null) "Audio engine could not start" else null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _lastEvent = MutableStateFlow<MidiEvent?>(null)
    val lastEvent: StateFlow<MidiEvent?> = _lastEvent.asStateFlow()

    // Conflated so rapid slider changes collapse into one engine update.
    private val pendingApply = MutableStateFlow<PerformancePreset?>(null)

    // Confined to engineThread.
    private val engineIds = HashMap<String, Int>()
    private var applied: PerformancePreset? = null
    private val removingSoundFonts = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var echo: MidiEcho? = null

    fun start() {
        scope.launch(engineThread) { pendingApply.filterNotNull().collect { applyToEngine(it) } }
        scope.launch {
            soundFonts.load()
            try {
                val rejected = presets.load()
                if (rejected.isNotEmpty()) _message.value = "Skipped invalid presets: ${rejected.joinToString("; ")}"
            } catch (e: PresetFormatException) {
                _message.value = e.message
            }
            presets.presets.value.firstOrNull()?.let { _current.value = it }
            commit(_current.value)
            if (synth != null) {
                withContext(engineThread) {
                    val failed = soundFonts.entries.value.filter {
                        ensureLoaded(it.id) == null && it.id !in removingSoundFonts && soundFonts.file(it.id) != null
                    }
                    if (failed.isNotEmpty()) {
                        val error = "Could not load instruments from: ${failed.joinToString { it.name }}"
                        _message.update { previous -> listOfNotNull(previous, error).joinToString("\n") }
                    }
                }
            }
        }
    }

    // ---- Editing -------------------------------------------------------------------------

    fun update(transform: (PerformancePreset) -> PerformancePreset) {
        var next: PerformancePreset? = null
        _current.update { old -> transform(old).takeIf { it.problems().isEmpty() }.also { next = it } ?: old }
        next?.let(::commit)
    }

    fun updateSlot(id: VoiceSlotId, transform: (VoiceSlot) -> VoiceSlot) =
        update { it.withSlot(id, transform(it.slot(id))) }

    fun selectInstrument(slot: VoiceSlotId, soundFontId: String, patch: Patch) =
        updateSlot(slot) { it.copy(soundFontId = soundFontId, bank = patch.bank, program = patch.program) }

    fun dismissMessage() {
        _message.value = null
    }

    // ---- Presets ---------------------------------------------------------------------------

    fun recall(id: String) {
        val preset = presets.presets.value.firstOrNull { it.id == id } ?: return
        _current.value = preset
        commit(preset)
    }

    fun save() = persist { presets.save(_current.value) }

    fun saveAsNew(name: String) = persist { _current.value = presets.create(name, _current.value) }

    fun delete(id: String) = persist {
        presets.delete(id)
        if (_current.value.id == id) _current.update { it.copy(id = newId()) }
    }

    private fun persist(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: IOException) {
                _message.value = "Could not save presets: ${e.message}"
            } catch (e: IllegalArgumentException) {
                _message.value = "Invalid preset: ${e.message}"
            }
        }
    }

    // ---- SoundFonts ------------------------------------------------------------------------

    suspend fun loadSoundFontPatches(id: String): Boolean = withContext(engineThread) {
        ensureLoaded(id) != null
    }

    fun renameSoundFont(id: String, name: String) = editSoundFont {
        soundFonts.rename(id, name)
    }

    fun renameInstrument(id: String, bank: Int, program: Int, name: String?) = editSoundFont {
        soundFonts.renameInstrument(id, bank, program, name)
    }

    private fun editSoundFont(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: IOException) {
                _message.value = "Could not save SoundFont names: ${e.message}"
            } catch (e: IllegalArgumentException) {
                _message.value = "Could not save SoundFont names: ${e.message}"
            }
        }
    }

    /** Copies a SoundFont into app storage, verifies it loads, and assigns it to empty slots. */
    fun importSoundFont(name: String, open: () -> InputStream?) {
        val engine = synth ?: return
        _importing.value = true
        scope.launch(Dispatchers.IO) {
            val id = newId()
            val file = soundFonts.newFile(id)
            try {
                (open() ?: throw IOException("Cannot open $name")).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val loaded = withContext(engineThread) {
                    engine.loadSoundFont(file.absolutePath)?.also { engineId ->
                        engineIds[id] = engineId
                        _patches.update { it + (id to engine.patches(engineId)) }
                        applied = null
                    }
                }
                if (loaded == null) {
                    file.delete()
                    _message.value = "$name is not a supported SoundFont (SF2/SF3)"
                    return@launch
                }
                soundFonts.add(SoundFontEntry(id, name, file.name))
                val first = _patches.value[id]?.firstOrNull()
                update { preset ->
                    VoiceSlotId.entries.fold(preset) { acc, slotId ->
                        val slot = acc.slot(slotId)
                        if (slot.soundFontId != null && soundFonts.file(slot.soundFontId) != null) acc
                        else acc.withSlot(slotId, slot.copy(soundFontId = id, bank = first?.bank ?: 0, program = first?.program ?: 0))
                    }
                }
            } catch (e: IOException) {
                file.delete()
                _message.value = "Could not import $name: ${e.message}"
            } finally {
                _importing.value = false
            }
        }
    }

    /** Names of saved presets (and the unsaved working setup) that reference [soundFontId]. */
    fun soundFontUsers(soundFontId: String): List<String> {
        val saved = presets.presets.value.filter { soundFontId in it.soundFontIds() }.map { it.name }
        val current = _current.value
        val unsaved = soundFontId in current.soundFontIds() && presets.presets.value.none { it == current }
        return if (unsaved) saved + "current unsaved setup" else saved
    }

    /** Referencing slots stay as-is and show "Missing SoundFont" until reassigned. */
    fun removeSoundFont(soundFontId: String) {
        if (!removingSoundFonts.add(soundFontId)) return
        scope.launch {
            try {
                panic()
                withContext(engineThread) {
                    engineIds.remove(soundFontId)?.let { synth?.unloadSoundFont(it) }
                    _patches.update { it - soundFontId }
                    applied = null
                }
                try {
                    soundFonts.remove(soundFontId)
                } catch (e: IOException) {
                    _message.value = "Could not remove SoundFont: ${e.message}"
                }
            } finally {
                removingSoundFonts.remove(soundFontId)
            }
            commit(_current.value)
        }
    }

    // ---- MIDI path -------------------------------------------------------------------------

    /** Echoes routed notes, pedals, bend, program and volume to an external device; null turns it off. */
    fun setEcho(send: ((status: Int, data1: Int, data2: Int) -> Unit)?) {
        echo?.silence()
        echo = send?.let { MidiEcho(it).apply { setup(_current.value) } }
    }

    /** Re-sends programs and volumes, e.g. after the device reconnects. */
    fun resendEchoSetup() {
        echo?.setup(_current.value, force = true)
    }

    fun onMidiEvent(event: MidiEvent) {
        _lastEvent.value = event
        when (event) {
            is MidiEvent.NoteOn -> dispatch(synchronized(router) { router.noteOn(event.note, event.velocity) })
            is MidiEvent.NoteOff -> dispatch(synchronized(router) { router.noteOff(event.note) })
            is MidiEvent.ControlChange -> when (event.controller) {
                CC_ALL_SOUND_OFF, CC_ALL_NOTES_OFF -> panic()
                in PERFORMANCE_CCS -> {
                    VoiceSlotId.entries.forEach { synth?.controlChange(it, event.controller, event.value) }
                    echo?.controlChange(event.controller, event.value)
                }
            }
            is MidiEvent.PitchBend -> {
                VoiceSlotId.entries.forEach { synth?.pitchBend(it, event.value) }
                echo?.pitchBend(event.value)
            }
        }
    }

    /** Releases held notes and resets pedal/bend; used on disconnect, driver switch, and app stop. */
    fun onConnectionLost() {
        dispatch(synchronized(router) { router.allNotesOff() })
        VoiceSlotId.entries.forEach {
            synth?.controlChange(it, CC_SUSTAIN, 0)
            synth?.pitchBend(it, 0)
        }
        echo?.run {
            controlChange(CC_SUSTAIN, 0)
            pitchBend(0)
        }
    }

    fun panic() {
        onConnectionLost()
        synth?.allSoundsOff()
        echo?.silence()
    }

    private fun dispatch(events: List<RoutedEvent>) {
        echo?.routed(events)
        val engine = synth ?: return
        for (e in events) {
            when (e) {
                is RoutedEvent.NoteOn -> engine.noteOn(e.target.slot, e.target.note, e.velocity)
                is RoutedEvent.NoteOff -> engine.noteOff(e.target.slot, e.target.note)
            }
        }
    }

    // ---- Engine ----------------------------------------------------------------------------

    private fun commit(preset: PerformancePreset) {
        synchronized(router) { router.config = preset.toRoutingConfig() }
        pendingApply.value = preset
        echo?.setup(preset)
    }

    private fun applyToEngine(p: PerformancePreset) {
        val engine = synth ?: return
        val prev = applied
        var complete = true
        for (id in VoiceSlotId.entries) {
            val slot = p.slot(id)
            val old = prev?.slot(id)
            if (old == null || old.soundFontId != slot.soundFontId || old.bank != slot.bank || old.program != slot.program) {
                val sf = slot.soundFontId?.let(::ensureLoaded)
                if (sf != null) engine.selectProgram(id, sf, slot.bank, slot.program) else if (slot.soundFontId != null) complete = false
            }
            if (old == null || old.volume != slot.volume) engine.setSlotVolume(id, slot.volume)
        }
        if (prev == null || prev.masterReverb != p.masterReverb) engine.setReverb(p.masterReverb)
        if (prev == null || prev.masterChorus != p.masterChorus) engine.setChorus(p.masterChorus)
        applied = if (complete) p else null
    }

    private fun ensureLoaded(soundFontId: String): Int? {
        if (soundFontId in removingSoundFonts) return null
        engineIds[soundFontId]?.let { return it }
        val engine = synth ?: return null
        val file = soundFonts.file(soundFontId) ?: return null
        val engineId = engine.loadSoundFont(file.absolutePath) ?: return null
        engineIds[soundFontId] = engineId
        _patches.update { it + (soundFontId to engine.patches(engineId)) }
        return engineId
    }

    private companion object {
        const val CC_MODULATION = 1
        const val CC_EXPRESSION = 11
        const val CC_SUSTAIN = 64
        const val CC_SOSTENUTO = 66
        const val CC_SOFT = 67
        const val CC_ALL_SOUND_OFF = 120
        const val CC_ALL_NOTES_OFF = 123
        val PERFORMANCE_CCS = setOf(CC_MODULATION, CC_EXPRESSION, CC_SUSTAIN, CC_SOSTENUTO, CC_SOFT)

        fun newId() = UUID.randomUUID().toString()
    }
}
