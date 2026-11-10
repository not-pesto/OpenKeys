package io.github.openkeys

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.openkeys.audio.Patch
import io.github.openkeys.instruments.InstrumentCategory
import io.github.openkeys.midi.MidiConnectionState
import io.github.openkeys.midi.MidiDriverType
import io.github.openkeys.midi.MidiEvent
import io.github.openkeys.midi.MidiInput
import io.github.openkeys.midi.MidiNote
import io.github.openkeys.presets.PerformancePreset
import io.github.openkeys.presets.VoiceSlot
import io.github.openkeys.routing.VoiceSlotId
import io.github.openkeys.soundfonts.SoundFontEntry
import io.github.openkeys.ui.artRes
import io.github.openkeys.ui.theme.OkColors
import io.github.openkeys.ui.theme.OpenKeysTheme
import io.github.openkeys.ui.theme.okSwitchColors

class MainActivity : ComponentActivity() {

    private val app get() = application as OpenKeysApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            OpenKeysTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = OkColors.Background) {
                    PerformanceScreen(app, ::displayName)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.performance.panic()
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "SoundFont"
}

private const val TAGLINE = "Open Source MIDI Platform for Music Performers"

@Composable
private fun PerformanceScreen(app: OpenKeysApp, displayName: (Uri) -> String) {
    val perf = app.performance
    val preset by perf.current.collectAsState()
    val presets by perf.presetList.collectAsState()
    val soundFonts by perf.soundFontList.collectAsState()
    val patches by perf.patches.collectAsState()
    val message by perf.message.collectAsState()
    val importing by perf.importing.collectAsState()
    val lastEvent by perf.lastEvent.collectAsState()
    val midi by app.midi.collectAsState()
    val driver by app.midiDriver.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pickSoundFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perf.importSoundFont(displayName(uri)) { context.contentResolver.openInputStream(uri) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth > 800.dp
        val compact = maxWidth < 600.dp
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                wide = wide,
                compact = compact,
                midi = midi,
                onSettings = { showSettings = true },
                onPanic = perf::panic,
            )

            message?.let {
                OkCard(borderColor = OkColors.Error) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = OkColors.Error, modifier = Modifier.weight(1f))
                        TextButton(onClick = perf::dismissMessage) { Text("Dismiss") }
                    }
                }
            }

            if (presets.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { p ->
                        if (p.id == preset.id) {
                            Button(onClick = { perf.recall(p.id) }, shape = MaterialTheme.shapes.small) { Text(p.name) }
                        } else {
                            OutlinedButton(onClick = { perf.recall(p.id) }, shape = MaterialTheme.shapes.small) { Text(p.name) }
                        }
                    }
                }
            }

            Box {
                val slots: @Composable (Modifier) -> Unit = { mod ->
                    VoiceSlotId.entries.forEach { id ->
                        SlotCard(
                            modifier = mod,
                            label = id.label(),
                            slot = preset.slot(id),
                            inactive = id == VoiceSlotId.LEFT_1 && !preset.splitEnabled,
                            compact = compact,
                            soundFonts = soundFonts,
                            patches = preset.slot(id).soundFontId?.let { patches[it] }.orEmpty(),
                            onChange = { transform -> perf.updateSlot(id, transform) },
                            onSelectSoundFont = { sf -> perf.selectSoundFont(id, sf) },
                        )
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { slots(Modifier.weight(1f)) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { slots(Modifier.fillMaxWidth()) }
                }
            }

            SplitSection(
                enabled = preset.splitEnabled,
                splitNote = preset.splitNote,
                onToggle = { on -> perf.update { it.copy(splitEnabled = on) } },
                onStep = { d -> perf.update { it.copy(splitNote = (it.splitNote + d).coerceIn(MidiNote.MIN, MidiNote.MAX)) } },
            )

            PresetSection(
                preset = preset,
                presets = presets,
                onRecall = perf::recall,
                onRename = { name -> perf.update { it.copy(name = name) } },
                onSave = perf::save,
                onSaveAsNew = perf::saveAsNew,
                onDelete = perf::delete,
            )

            OkCard {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("Master effects")
                    LabeledSlider("Reverb", preset.masterReverb) { v -> perf.update { it.copy(masterReverb = v) } }
                    LabeledSlider("Chorus", preset.masterChorus) { v -> perf.update { it.copy(masterChorus = v) } }
                }
            }

            MidiSection(midi, lastEvent)

            SoundFontSection(
                soundFonts = soundFonts,
                importing = importing,
                onImport = { pickSoundFont.launch(arrayOf("*/*")) },
                usersOf = perf::soundFontUsers,
                onRemove = perf::removeSoundFont,
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            driver = driver,
            onSelect = app::setMidiDriver,
            onShowLicenses = { showSettings = false; showLicenses = true },
            onDismiss = { showSettings = false },
        )
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun Header(wide: Boolean, compact: Boolean, midi: MidiInput, onSettings: () -> Unit, onPanic: () -> Unit) {
    val state by midi.state.collectAsState()
    val brand: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.openkeys_mark),
                contentDescription = null,
                modifier = Modifier.height(if (compact) 28.dp else 36.dp).aspectRatio(MARK_ASPECT),
            )
            Text(
                buildAnnotatedString {
                    append("Open")
                    withStyle(SpanStyle(color = OkColors.Cyan)) { append("Keys") }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
            )
            if (wide) {
                Box(Modifier.width(1.dp).height(32.dp).background(OkColors.Border))
                Text(TAGLINE, style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary, modifier = Modifier.width(180.dp))
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MidiStatusChip(state)
            OutlinedButton(onClick = onSettings, shape = MaterialTheme.shapes.small) { Text("Settings") }
            Button(onClick = onPanic, shape = MaterialTheme.shapes.small) { Text("All notes off") }
        }
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            brand()
            controls()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            brand()
            Spacer(Modifier.weight(1f))
            controls()
        }
    }
    HorizontalDivider(color = OkColors.Border)
}

private const val MARK_ASPECT = 715f / 245f

@Composable
private fun MidiStatusChip(state: MidiConnectionState) {
    val (label, color) = when (state) {
        is MidiConnectionState.Connected -> "MIDI connected" to OkColors.Success
        is MidiConnectionState.Connecting -> "Connecting…" to OkColors.Cyan
        MidiConnectionState.Disconnected -> "No MIDI device" to OkColors.TextMuted
        is MidiConnectionState.Error -> "MIDI error" to OkColors.Error
    }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .background(color.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (state is MidiConnectionState.Disconnected) OkColors.TextSecondary else color,
        )
    }
}

@Composable
private fun OkCard(
    modifier: Modifier = Modifier,
    borderColor: Color = OkColors.Border,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = OkColors.Card,
        contentColor = OkColors.TextPrimary,
        border = BorderStroke(borderWidth, borderColor),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = OkColors.TextSecondary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

@Composable
private fun SplitSection(enabled: Boolean, splitNote: Int, onToggle: (Boolean) -> Unit, onStep: (Int) -> Unit) {
    OkCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keyboard split", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle, colors = okSwitchColors())
                Spacer(Modifier.width(24.dp))
                Text("Split point", color = OkColors.TextSecondary)
                Stepper(
                    label = MidiNote.name(splitNote),
                    enabled = enabled,
                    steps = listOf(-12, -1, 1, 12),
                    onStep = onStep,
                )
            }
            SplitBar(enabled, splitNote)
        }
    }
}

@Composable
private fun SplitBar(enabled: Boolean, splitNote: Int) {
    val left = if (enabled) splitNote / 128f else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            if (left > 0f) {
                Box(Modifier.weight(left).fillMaxHeight().background(OkColors.Cyan.copy(alpha = 0.7f)))
                Box(Modifier.width(2.dp).fillMaxHeight().background(OkColors.TextPrimary))
            }
            Box(
                Modifier.weight(1f - left).fillMaxHeight()
                    .background(if (enabled) OkColors.Primary.copy(alpha = 0.8f) else OkColors.Border),
            )
        }
        Row {
            if (enabled) {
                Text("Left 1 · below ${MidiNote.name(splitNote)}", style = MaterialTheme.typography.bodySmall, color = OkColors.Cyan)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (enabled) "Right 1 + Right 2" else "Right 1 + Right 2 · full keyboard",
                style = MaterialTheme.typography.bodySmall,
                color = OkColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SoundFontSection(
    soundFonts: List<SoundFontEntry>,
    importing: Boolean,
    onImport: () -> Unit,
    usersOf: (String) -> List<String>,
    onRemove: (String) -> Unit,
) {
    var removing by remember { mutableStateOf<SoundFontEntry?>(null) }
    OkCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("SoundFonts", Modifier.weight(1f))
                Button(onClick = onImport, enabled = !importing, shape = MaterialTheme.shapes.small) {
                    Text(if (importing) "Importing…" else "Import SoundFont")
                }
            }
            if (soundFonts.isEmpty()) Text("Import an SF2 or SF3 file to start playing.", color = OkColors.TextSecondary)
            soundFonts.forEach { sf ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sf.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { removing = sf }) { Text("Remove") }
                }
            }
        }
    }
    removing?.let { sf ->
        val users = remember(sf.id) { usersOf(sf.id) }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${sf.name}?") },
            text = {
                Text(
                    if (users.isEmpty()) "The file will be deleted from the app."
                    else "Used by: ${users.joinToString()}. Those voices will show \"Missing SoundFont\" until you pick another.",
                )
            },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(sf.id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val assets = androidx.compose.ui.platform.LocalContext.current.assets
    val files = remember { assets.list("licenses")?.sorted().orEmpty() }
    var selected by remember { mutableStateOf(files.firstOrNull { it.startsWith("THIRD_PARTY") } ?: files.firstOrNull()) }
    val text = remember(selected) {
        selected?.let { name -> assets.open("licenses/$name").bufferedReader().use { it.readText() } }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About & licences") },
        text = {
            Column {
                Text(TAGLINE, color = OkColors.Cyan, style = MaterialTheme.typography.bodyMedium)
                Text("OpenKeys ${BuildConfig.VERSION_NAME}. Apache-2.0. Uses FluidSynth (LGPL-2.1) and Kshoji USB-MIDI-Driver (Apache-2.0).")
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    files.forEach { f ->
                        TextButton(onClick = { selected = f }) {
                            Text(f.substringBeforeLast('.'), style = if (f == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun MidiSection(midi: MidiInput, lastEvent: MidiEvent?) {
    val state by midi.state.collectAsState()
    val devices by midi.devices.collectAsState()
    OkCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("MIDI input")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (val s = state) {
                        MidiConnectionState.Disconnected ->
                            if (midi.supportsDeviceSelection) "No MIDI device connected" else "Waiting for USB MIDI device…"
                        is MidiConnectionState.Connecting -> "Connecting to ${s.deviceName}…"
                        is MidiConnectionState.Connected -> "Connected: ${s.deviceName}"
                        is MidiConnectionState.Error -> s.message
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state is MidiConnectionState.Error) OkColors.Error else OkColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state is MidiConnectionState.Connected) {
                    OutlinedButton(onClick = midi::disconnect, shape = MaterialTheme.shapes.small) { Text("Disconnect") }
                } else if (!midi.supportsDeviceSelection) {
                    OutlinedButton(onClick = { midi.connect(0) }, shape = MaterialTheme.shapes.small) { Text("Scan") }
                }
            }
            if (midi.supportsDeviceSelection && state !is MidiConnectionState.Connected) {
                if (devices.isEmpty()) Text("No MIDI input devices found. Try the USB MIDI Driver in Settings.", color = OkColors.TextSecondary)
                devices.forEach { d -> TextButton(onClick = { midi.connect(d.id) }) { Text(d.name) } }
            }
            Text(
                text = lastEvent?.let(::describe) ?: "Play a key to test input",
                style = MaterialTheme.typography.bodySmall,
                color = if (lastEvent != null) OkColors.Cyan else OkColors.TextMuted,
            )
        }
    }
}

@Composable
private fun PresetSection(
    preset: PerformancePreset,
    presets: List<PerformancePreset>,
    onRecall: (String) -> Unit,
    onRename: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAsNew: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    val saved = presets.firstOrNull { it.id == preset.id }
    OkCard {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Picker(
                label = if (saved == null) "Presets (${presets.size})" else "Preset",
                selected = saved?.name ?: "Unsaved",
                options = presets.map { it.id to it.name },
                onSelect = onRecall,
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) onRename(it)
                },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave, enabled = name.isNotBlank() && saved != preset, shape = MaterialTheme.shapes.small) {
                Text(if (saved == null) "Save" else if (saved == preset) "Saved" else "Save*")
            }
            OutlinedButton(onClick = { onSaveAsNew("$name copy") }, enabled = name.isNotBlank(), shape = MaterialTheme.shapes.small) {
                Text("Save as new")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = saved != null) { Text("Delete") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete preset?") },
            text = { Text("\"${preset.name}\" will be removed. The current sound settings stay loaded.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(preset.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SlotCard(
    modifier: Modifier,
    label: String,
    slot: VoiceSlot,
    inactive: Boolean,
    compact: Boolean,
    soundFonts: List<SoundFontEntry>,
    patches: List<Patch>,
    onChange: ((VoiceSlot) -> VoiceSlot) -> Unit,
    onSelectSoundFont: (String) -> Unit,
) {
    val patch = patches.firstOrNull { it.bank == slot.bank && it.program == slot.program }
    val sfName = slot.soundFontId?.let { id -> soundFonts.firstOrNull { it.id == id }?.name ?: "Missing SoundFont" } ?: "No SoundFont"
    val art = remember(slot.soundFontId, slot.bank, slot.program, patch?.name) {
        slot.soundFontId?.let { InstrumentCategory.of(slot.bank, slot.program, patch?.name)?.artRes() }
    }
    val active = slot.enabled && !inactive
    OkCard(
        modifier = modifier,
        borderColor = if (active) OkColors.Primary else OkColors.Border,
        borderWidth = if (active) 2.dp else 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(label, Modifier.weight(1f), color = if (active) OkColors.Cyan else OkColors.TextSecondary)
                Switch(
                    checked = slot.enabled,
                    onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                    colors = okSwitchColors(),
                )
            }
            HorizontalDivider(color = OkColors.Border)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val imageSize = (maxWidth * if (compact) 0.18f else 0.26f).coerceIn(40.dp, 112.dp)
                Row(
                    Modifier.alpha(if (active) 1f else 0.6f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            patch?.name ?: if (slot.soundFontId == null) "No instrument" else "Program ${slot.program}",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(sfName, style = MaterialTheme.typography.bodySmall, color = OkColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            inactive -> Text("Inactive while split is off", style = MaterialTheme.typography.bodySmall, color = OkColors.Warning)
                            !slot.enabled -> Text("Off", style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary)
                        }
                    }
                    if (art != null) {
                        Spacer(Modifier.width(12.dp))
                        InstrumentImage(art, Modifier.size(imageSize))
                    }
                }
            }
            Picker(
                label = "SoundFont",
                selected = sfName,
                options = soundFonts.map { it.id to it.name },
                onSelect = onSelectSoundFont,
                fill = true,
            )
            Picker(
                label = "Instrument",
                selected = patch?.let(::patchLabel) ?: "${slot.bank}:${slot.program}",
                options = patches.map { "${it.bank}:${it.program}" to patchLabel(it) },
                onSelect = { key ->
                    val (bank, program) = key.split(":").map(String::toInt)
                    onChange { it.copy(bank = bank, program = program) }
                },
                fill = true,
            )
            HorizontalDivider(color = OkColors.Border)
            LabeledSlider("Vol", slot.volume) { v -> onChange { it.copy(volume = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transpose", color = OkColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Stepper(
                    label = "%+d".format(slot.transposeSemitones),
                    enabled = true,
                    steps = listOf(-12, -1, 1, 12),
                    onStep = { d ->
                        onChange {
                            it.copy(transposeSemitones = (it.transposeSemitones + d).coerceIn(-VoiceSlot.MAX_TRANSPOSE, VoiceSlot.MAX_TRANSPOSE))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InstrumentImage(@DrawableRes res: Int, modifier: Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alpha = 0.85f,
        modifier = modifier,
    )
}

@Composable
private fun Picker(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    fill: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(if (fill) Modifier.fillMaxWidth() else Modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = options.isNotEmpty(),
            shape = MaterialTheme.shapes.small,
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        ) {
            Text(label, color = OkColors.TextMuted, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(selected, color = OkColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(key) })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, steps: List<Int>, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        steps.filter { it < 0 }.forEach { TextButton(onClick = { onStep(it) }, enabled = enabled) { Text("$it") } }
        Text(label, style = MaterialTheme.typography.titleMedium)
        steps.filter { it > 0 }.forEach { TextButton(onClick = { onStep(it) }, enabled = enabled) { Text("+$it") } }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OkColors.TextSecondary, modifier = Modifier.width(64.dp))
        Text("${(value * 100).toInt()}%", modifier = Modifier.width(48.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsDialog(
    driver: MidiDriverType,
    onSelect: (MidiDriverType) -> Unit,
    onShowLicenses: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MIDI input driver") },
        text = {
            Column {
                DriverOption(
                    selected = driver == MidiDriverType.ANDROID_MIDI,
                    title = "Android MIDI",
                    detail = "Standard class-compliant USB MIDI devices.",
                    onClick = { onSelect(MidiDriverType.ANDROID_MIDI) },
                )
                DriverOption(
                    selected = driver == MidiDriverType.USB_MIDI_DRIVER,
                    title = "USB MIDI Driver (Kshoji)",
                    detail = "Direct USB access. Use for keyboards Android does not list, e.g. Yamaha PSR series.",
                    onClick = { onSelect(MidiDriverType.USB_MIDI_DRIVER) },
                )
                TextButton(onClick = onShowLicenses) { Text("About & licences") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DriverOption(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary)
        }
    }
}

private fun VoiceSlotId.label() = when (this) {
    VoiceSlotId.RIGHT_1 -> "Right 1"
    VoiceSlotId.RIGHT_2 -> "Right 2"
    VoiceSlotId.LEFT_1 -> "Left 1"
}

private fun patchLabel(p: Patch) = "%03d:%03d %s".format(p.bank, p.program, p.name)

private fun describe(event: MidiEvent): String = when (event) {
    is MidiEvent.NoteOn -> "Note on ${MidiNote.name(event.note)} vel ${event.velocity} ch ${event.channel + 1}"
    is MidiEvent.NoteOff -> "Note off ${MidiNote.name(event.note)} ch ${event.channel + 1}"
    is MidiEvent.ControlChange -> "CC ${event.controller} = ${event.value} ch ${event.channel + 1}"
    is MidiEvent.PitchBend -> "Pitch bend ${event.value} ch ${event.channel + 1}"
}
