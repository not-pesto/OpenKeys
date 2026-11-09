package io.github.openkeys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.openkeys.midi.MidiConnectionState
import io.github.openkeys.midi.MidiDeviceEntry
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.midi.MidiNote

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenKeysApp
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val devices by app.midi.devices.collectAsState()
                    val state by app.midi.state.collectAsState()
                    val lastEvent by app.lastEvent.collectAsState()
                    val sound by app.sound.collectAsState()
                    val pickSoundFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        uri?.let(app::loadSoundFont)
                    }
                    MidiConnectionScreen(
                        devices = devices,
                        state = state,
                        lastEvent = lastEvent,
                        sound = sound,
                        onConnect = { app.midi.connect(it) },
                        onDisconnect = { app.midi.disconnect() },
                        onPickSoundFont = { pickSoundFont.launch(arrayOf("*/*")) },
                        onPanic = app::panic,
                    )
                }
            }
        }
    }
}

@Composable
private fun MidiConnectionScreen(
    devices: List<MidiDeviceEntry>,
    state: MidiConnectionState,
    lastEvent: MidiEvent?,
    sound: SoundStatus,
    onConnect: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onPickSoundFont: () -> Unit,
    onPanic: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = when (sound) {
                is SoundStatus.EngineFailed -> sound.message
                SoundStatus.NoSoundFont -> stringResource(R.string.sound_none)
                SoundStatus.Loading -> stringResource(R.string.sound_loading)
                is SoundStatus.Loaded -> stringResource(R.string.sound_loaded, sound.name)
                is SoundStatus.LoadFailed -> sound.message
            },
            color = if (sound is SoundStatus.EngineFailed || sound is SoundStatus.LoadFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPickSoundFont, enabled = sound !is SoundStatus.EngineFailed && sound != SoundStatus.Loading) {
                Text(stringResource(R.string.sound_pick))
            }
            Button(onClick = onPanic) { Text(stringResource(R.string.panic)) }
        }
        Text(
            text = when (state) {
                MidiConnectionState.Disconnected -> stringResource(R.string.midi_disconnected)
                is MidiConnectionState.Connecting -> stringResource(R.string.midi_connecting, state.deviceName)
                is MidiConnectionState.Connected -> stringResource(R.string.midi_connected, state.deviceName)
                is MidiConnectionState.Error -> state.message
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (state is MidiConnectionState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (state is MidiConnectionState.Connected) {
            Button(onClick = onDisconnect) { Text(stringResource(R.string.midi_disconnect)) }
            Text(lastEvent?.let(::describe) ?: stringResource(R.string.midi_waiting))
        }
        Text(stringResource(R.string.midi_devices), style = MaterialTheme.typography.titleSmall)
        if (devices.isEmpty()) {
            Text(stringResource(R.string.midi_no_devices))
        } else {
            LazyColumn {
                items(devices, key = { it.id }) { device ->
                    Text(
                        text = device.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnect(device.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun describe(event: MidiEvent): String = when (event) {
    is MidiEvent.NoteOn -> "Note on ${MidiNote.name(event.note)} vel ${event.velocity}"
    is MidiEvent.NoteOff -> "Note off ${MidiNote.name(event.note)}"
    is MidiEvent.ControlChange -> "CC ${event.controller} = ${event.value}"
    is MidiEvent.PitchBend -> "Pitch bend ${event.value}"
}
