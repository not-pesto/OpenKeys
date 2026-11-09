package io.github.openkeys

import android.app.Application
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.midi.MidiInputManager
import io.github.openkeys.routing.NoteRouter
import io.github.openkeys.routing.RoutedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OpenKeysApp : Application() {

    private val router = NoteRouter()

    private val _lastEvent = MutableStateFlow<MidiEvent?>(null)
    val lastEvent: StateFlow<MidiEvent?> = _lastEvent.asStateFlow()

    lateinit var midi: MidiInputManager
        private set

    override fun onCreate() {
        super.onCreate()
        midi = MidiInputManager(this, ::onMidiEvent, ::onConnectionLost)
        midi.start()
    }

    private fun onMidiEvent(event: MidiEvent) {
        _lastEvent.value = event
        val routed = synchronized(router) {
            when (event) {
                is MidiEvent.NoteOn -> router.noteOn(event.note, event.velocity)
                is MidiEvent.NoteOff -> router.noteOff(event.note)
                is MidiEvent.ControlChange ->
                    if (event.controller == CC_ALL_SOUND_OFF || event.controller == CC_ALL_NOTES_OFF) router.allNotesOff()
                    else emptyList()
                is MidiEvent.PitchBend -> emptyList()
            }
        }
        dispatch(routed)
    }

    private fun onConnectionLost() {
        dispatch(synchronized(router) { router.allNotesOff() })
    }

    @Suppress("UNUSED_PARAMETER")
    private fun dispatch(events: List<RoutedEvent>) {
        // Synth engine is wired in the audio stage.
    }

    private companion object {
        const val CC_ALL_SOUND_OFF = 120
        const val CC_ALL_NOTES_OFF = 123
    }
}
