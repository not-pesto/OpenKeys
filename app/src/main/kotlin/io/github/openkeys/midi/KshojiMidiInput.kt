package io.github.openkeys.midi

import android.content.Context
import android.hardware.usb.UsbDevice
import jp.kshoji.driver.midi.device.MidiInputDevice
import jp.kshoji.driver.midi.device.MidiOutputDevice
import jp.kshoji.driver.midi.util.UsbMidiDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * USB MIDI input via the Kshoji USB-MIDI-Driver (Android USB Host API). Reads standard USB-MIDI 1.0
 * event packets, including from devices with vendor-specific interface classes (e.g. Yamaha).
 * Matching devices connect automatically once the user grants USB permission.
 */
class KshojiMidiInput(
    context: Context,
    private val onEvent: (MidiEvent) -> Unit,
    private val onConnectionLost: () -> Unit,
) : MidiInput {

    private val appContext = context.applicationContext
    // open()/close() can block for about a second while the driver's watcher thread stops.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "openkeys-usb-midi") }
    private var driver: Driver? = null
    private val inputs = ConcurrentHashMap<MidiInputDevice, MidiDeviceEntry>()

    private val _devices = MutableStateFlow<List<MidiDeviceEntry>>(emptyList())
    override val devices: StateFlow<List<MidiDeviceEntry>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<MidiConnectionState>(MidiConnectionState.Disconnected)
    override val state: StateFlow<MidiConnectionState> = _state.asStateFlow()

    override val supportsDeviceSelection = false

    override fun start() {
        worker.execute {
            if (driver == null) driver = Driver().apply { open() }
        }
    }

    override fun connect(deviceId: Int) = start()

    override fun disconnect() {
        worker.execute { closeDriver() }
    }

    override fun release() {
        worker.execute { closeDriver() }
        worker.shutdown()
    }

    private fun closeDriver() {
        val d = driver ?: return
        driver = null
        d.close()
        if (inputs.isNotEmpty()) onConnectionLost()
        inputs.clear()
        publish()
    }

    private fun publish() {
        val list = inputs.values.distinctBy { it.id }.sortedBy { it.name }
        _devices.value = list
        _state.value = if (list.isEmpty()) {
            MidiConnectionState.Disconnected
        } else {
            MidiConnectionState.Connected(list.joinToString { it.name })
        }
    }

    private fun entryFor(device: UsbDevice): MidiDeviceEntry {
        val product = device.productName?.takeIf { it.isNotBlank() } ?: "USB MIDI device"
        val maker = device.manufacturerName?.takeIf { it.isNotBlank() }
        val ids = "%04x:%04x".format(device.vendorId, device.productId)
        return MidiDeviceEntry(device.deviceId, listOfNotNull(maker, product).joinToString(" ") + " ($ids)")
    }

    private inner class Driver : UsbMidiDriver(appContext) {
        override fun onMidiInputDeviceAttached(midiInputDevice: MidiInputDevice) {
            inputs[midiInputDevice] = entryFor(midiInputDevice.usbDevice)
            publish()
        }

        override fun onMidiInputDeviceDetached(midiInputDevice: MidiInputDevice) {
            if (inputs.remove(midiInputDevice) != null) {
                onConnectionLost()
                publish()
            }
        }

        override fun onMidiNoteOn(sender: MidiInputDevice, cable: Int, channel: Int, note: Int, velocity: Int) =
            onEvent(if (velocity == 0) MidiEvent.NoteOff(channel, note, 0) else MidiEvent.NoteOn(channel, note, velocity))

        override fun onMidiNoteOff(sender: MidiInputDevice, cable: Int, channel: Int, note: Int, velocity: Int) =
            onEvent(MidiEvent.NoteOff(channel, note, velocity))

        override fun onMidiControlChange(sender: MidiInputDevice, cable: Int, channel: Int, function: Int, value: Int) =
            onEvent(MidiEvent.ControlChange(channel, function, value))

        override fun onMidiPitchWheel(sender: MidiInputDevice, cable: Int, channel: Int, amount: Int) =
            onEvent(MidiEvent.PitchBend(channel, amount - 8192))

        override fun onDeviceAttached(usbDevice: UsbDevice) = Unit
        override fun onDeviceDetached(usbDevice: UsbDevice) = Unit
        override fun onMidiOutputDeviceAttached(midiOutputDevice: MidiOutputDevice) = Unit
        override fun onMidiOutputDeviceDetached(midiOutputDevice: MidiOutputDevice) = Unit
        override fun onMidiMiscellaneousFunctionCodes(sender: MidiInputDevice, cable: Int, byte1: Int, byte2: Int, byte3: Int) = Unit
        override fun onMidiCableEvents(sender: MidiInputDevice, cable: Int, byte1: Int, byte2: Int, byte3: Int) = Unit
        override fun onMidiSystemCommonMessage(sender: MidiInputDevice, cable: Int, bytes: ByteArray) = Unit
        override fun onMidiSystemExclusive(sender: MidiInputDevice, cable: Int, systemExclusive: ByteArray) = Unit
        override fun onMidiPolyphonicAftertouch(sender: MidiInputDevice, cable: Int, channel: Int, note: Int, pressure: Int) = Unit
        override fun onMidiProgramChange(sender: MidiInputDevice, cable: Int, channel: Int, program: Int) = Unit
        override fun onMidiChannelAftertouch(sender: MidiInputDevice, cable: Int, channel: Int, pressure: Int) = Unit
        override fun onMidiSingleByte(sender: MidiInputDevice, cable: Int, byte1: Int) = Unit
        override fun onMidiTimeCodeQuarterFrame(sender: MidiInputDevice, cable: Int, timing: Int) = Unit
        override fun onMidiSongSelect(sender: MidiInputDevice, cable: Int, song: Int) = Unit
        override fun onMidiSongPositionPointer(sender: MidiInputDevice, cable: Int, position: Int) = Unit
        override fun onMidiTuneRequest(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiTimingClock(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiStart(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiContinue(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiStop(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiActiveSensing(sender: MidiInputDevice, cable: Int) = Unit
        override fun onMidiReset(sender: MidiInputDevice, cable: Int) = Unit
    }
}
