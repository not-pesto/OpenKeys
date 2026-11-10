package io.github.openkeys.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

data class MidiDeviceEntry(val id: Int, val name: String)

sealed interface MidiConnectionState {
    data object Disconnected : MidiConnectionState
    data class Connecting(val deviceName: String) : MidiConnectionState
    data class Connected(val deviceName: String) : MidiConnectionState
    data class Error(val message: String) : MidiConnectionState
}

/**
 * Discovers MIDI input devices and delivers parsed events from the selected one.
 * [onEvent] and [onConnectionLost] run on background threads that may differ from each other.
 */
class MidiInputManager(
    context: Context,
    private val onEvent: (MidiEvent) -> Unit,
    private val onConnectionLost: () -> Unit,
    private val onOutputReady: () -> Unit,
) : MidiInput {
    private val midiManager: MidiManager? = context.getSystemService(MidiManager::class.java)
    private val thread = HandlerThread("openkeys-midi").apply { start() }
    private val handler = Handler(thread.looper)

    private val _devices = MutableStateFlow<List<MidiDeviceEntry>>(emptyList())
    override val devices: StateFlow<List<MidiDeviceEntry>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<MidiConnectionState>(MidiConnectionState.Disconnected)
    override val state: StateFlow<MidiConnectionState> = _state.asStateFlow()

    override val supportsDeviceSelection = true

    // All fields below are confined to the MIDI handler thread.
    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var connectedId: Int? = null
    private var reconnectName: String? = null
    private var generation = 0

    // Written on the handler thread, read from any thread by send().
    @Volatile private var inputPort: MidiInputPort? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            refreshDevices()
            val name = reconnectName
            if (connectedId == null && name != null && info.outputPortCount > 0 && displayName(info) == name) {
                openDevice(info)
            }
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            refreshDevices()
            if (info.id == connectedId) {
                closeCurrent()
                onConnectionLost()
                _state.value = MidiConnectionState.Disconnected
            }
        }
    }

    override fun start() {
        val manager = midiManager ?: run {
            _state.value = MidiConnectionState.Error("MIDI is not supported on this device")
            return
        }
        handler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.registerDeviceCallback(
                    MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                    { handler.post(it) },
                    deviceCallback,
                )
            } else {
                @Suppress("DEPRECATION")
                manager.registerDeviceCallback(deviceCallback, handler)
            }
            refreshDevices()
        }
    }

    override fun connect(deviceId: Int) {
        handler.post {
            val info = currentDeviceInfos().firstOrNull { it.id == deviceId }
            if (info == null) {
                _state.value = MidiConnectionState.Error("Device is no longer available")
                refreshDevices()
            } else {
                reconnectName = displayName(info)
                openDevice(info)
            }
        }
    }

    override fun disconnect() {
        handler.post {
            reconnectName = null
            closeCurrent()
            onConnectionLost()
            _state.value = MidiConnectionState.Disconnected
        }
    }

    override fun release() {
        handler.post {
            midiManager?.unregisterDeviceCallback(deviceCallback)
            closeCurrent()
            thread.quitSafely()
        }
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val manager = midiManager ?: return
        closeCurrent()
        val name = displayName(info)
        val myGeneration = ++generation
        _state.value = MidiConnectionState.Connecting(name)
        manager.openDevice(info, { opened ->
            if (myGeneration != generation) {
                opened?.close()
                return@openDevice
            }
            if (opened == null) {
                _state.value = MidiConnectionState.Error("Could not open $name (permission denied or device busy)")
                return@openDevice
            }
            val outputPort = opened.openOutputPort(0)
            if (outputPort == null) {
                opened.close()
                _state.value = MidiConnectionState.Error("$name is in use by another app")
                return@openDevice
            }
            val parser = MidiParser(onEvent)
            outputPort.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parser.feed(msg, offset, count)
                }
            })
            device = opened
            port = outputPort
            connectedId = info.id
            _state.value = MidiConnectionState.Connected(name)
            if (info.inputPortCount > 0) {
                inputPort = opened.openInputPort(0)
                if (inputPort != null) onOutputReady()
            }
        }, handler)
    }

    override fun send(status: Int, data1: Int, data2: Int) {
        val p = inputPort ?: return
        val length = if (status and 0xE0 == 0xC0) 2 else 3
        try {
            p.send(byteArrayOf(status.toByte(), data1.toByte(), data2.toByte()), 0, length)
        } catch (_: IOException) {
            // Port closed while disconnecting.
        }
    }

    private fun closeCurrent() {
        generation++
        inputPort?.close()
        inputPort = null
        port?.close()
        device?.close()
        port = null
        device = null
        connectedId = null
    }

    private fun refreshDevices() {
        _devices.value = currentDeviceInfos()
            .filter { it.outputPortCount > 0 }
            .map { MidiDeviceEntry(it.id, displayName(it)) }
    }

    private fun currentDeviceInfos(): List<MidiDeviceInfo> {
        val manager = midiManager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            manager.devices.toList()
        }
    }

    private fun displayName(info: MidiDeviceInfo): String {
        val props = info.properties
        return props.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI device ${info.id}"
    }
}
