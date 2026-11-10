package io.github.openkeys.midi

import kotlinx.coroutines.flow.StateFlow

enum class MidiDriverType { ANDROID_MIDI, USB_MIDI_DRIVER }

/** A MIDI input backend. Events are delivered on background threads. */
interface MidiInput {
    val devices: StateFlow<List<MidiDeviceEntry>>
    val state: StateFlow<MidiConnectionState>

    /** False when devices connect automatically and [connect] is a no-op. */
    val supportsDeviceSelection: Boolean

    fun start()
    fun connect(deviceId: Int)
    fun disconnect()
    fun release()

    /** Sends a channel voice message to the connected device; no-op when it has no MIDI input. Thread-safe. */
    fun send(status: Int, data1: Int, data2: Int)
}
