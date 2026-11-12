package io.github.openkeys

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.launch

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

// Mirrors the keyboard: left-hand voice first.
private val TILE_ORDER = listOf(VoiceSlotId.LEFT_1, VoiceSlotId.RIGHT_1, VoiceSlotId.RIGHT_2)

private enum class Destination(val title: String, val symbol: String) {
    MIXER("Mixer", "≡"),
    EFFECTS("Effects", "◉"),
    PRESETS("List Presets", "☷"),
    SAVE("Save Preset", "▣"),
    SETTINGS("Settings", "⚙"),
}

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
    val echo by app.echoToKeyboard.collectAsState()
    val transpose by app.globalTranspose.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.MIXER) }
    var settingsTab by rememberSaveable { mutableStateOf(0) }
    var selectedSlot by remember { mutableStateOf<VoiceSlotId?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pickSoundFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perf.importSoundFont(displayName(uri)) { context.contentResolver.openInputStream(uri) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 480.dp && maxWidth >= maxHeight
        val compact = maxWidth < 600.dp || maxHeight < 500.dp
        val compactCards = maxWidth < 600.dp && !wide
        val wideHeader = maxWidth >= 800.dp
        Column(Modifier.fillMaxSize()) {
            Header(wide = wideHeader, compact = compact, midi = midi)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (destination) {
                    Destination.MIXER -> MixerScreen(
                        preset = preset,
                        presets = presets,
                        transpose = transpose,
                        soundFonts = soundFonts,
                        patches = patches,
                        wide = wide,
                        compact = compact,
                        compactCards = compactCards,
                        onRecall = perf::recall,
                        onBrowse = { destination = Destination.PRESETS },
                        onSlotChange = perf::updateSlot,
                        onSelectSlot = { selectedSlot = it },
                    )
                    Destination.EFFECTS -> EffectsScreen(preset) { transform -> perf.update(transform) }
                    Destination.PRESETS -> PresetLibrary(preset, presets, perf::recall, perf::delete)
                    Destination.SAVE -> SavePresetScreen(
                        preset, presets,
                        onRename = { name -> perf.update { it.copy(name = name) } },
                        onSave = perf::save,
                        onSaveAsNew = perf::saveAsNew,
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        midi = midi, lastEvent = lastEvent, onPanic = perf::panic,
                        driver = driver, onSelect = app::setMidiDriver, echo = echo,
                        onEcho = app::setEchoToKeyboard, soundFonts = soundFonts, patches = patches,
                        importing = importing, onImport = { pickSoundFont.launch(arrayOf("*/*")) },
                        usersOf = perf::soundFontUsers, onRemove = perf::removeSoundFont,
                        onRename = perf::renameSoundFont, onRenameInstrument = perf::renameInstrument,
                        loadPatches = perf::loadSoundFontPatches, preset = preset,
                        transpose = transpose, onTranspose = app::setGlobalTranspose,
                        onUpdate = perf::update,
                        selectedTab = settingsTab, onTabSelected = { settingsTab = it },
                    )
                }
            }
            BottomNavigation(destination, compact) { destination = it }
        }
    }

    selectedSlot?.let { id ->
        InstrumentDialog(
            id = id, slot = preset.slot(id), soundFonts = soundFonts, patches = patches,
            onSelect = { sf, patch -> perf.selectInstrument(id, sf, patch); selectedSlot = null },
            onDismiss = { selectedSlot = null },
            onManage = { selectedSlot = null; settingsTab = 3; destination = Destination.SETTINGS },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = perf::dismissMessage,
            title = { Text("OpenKeys") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = perf::dismissMessage) { Text("Dismiss") } },
        )
    }
}

@Composable
private fun MixerScreen(
    preset: PerformancePreset,
    presets: List<PerformancePreset>,
    transpose: Int,
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    wide: Boolean,
    compact: Boolean,
    compactCards: Boolean,
    onRecall: (String) -> Unit,
    onBrowse: () -> Unit,
    onSlotChange: (VoiceSlotId, (VoiceSlot) -> VoiceSlot) -> Unit,
    onSelectSlot: (VoiceSlotId) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Transpose %+d".format(transpose), color = OkColors.Cyan, style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEachIndexed { index, p ->
                    val title = "%03d %s".format(index + 1, p.name)
                    if (p.id == preset.id) {
                        Button(onClick = { onRecall(p.id) }, shape = MaterialTheme.shapes.small) { Text(title, maxLines = 1) }
                    } else {
                        OutlinedButton(onClick = { onRecall(p.id) }, shape = MaterialTheme.shapes.small) { Text(title, maxLines = 1) }
                    }
                }
                if (presets.isEmpty()) Text("No saved presets", color = OkColors.TextSecondary)
            }
            TextButton(onClick = onBrowse) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        val slotContent: @Composable (VoiceSlotId, Modifier) -> Unit = { id, modifier ->
            SlotCard(
                modifier = modifier,
                label = id.label(),
                slot = preset.slot(id),
                compact = compactCards,
                splitNote = if (id == VoiceSlotId.LEFT_1) preset.splitNote else null,
                soundFonts = soundFonts,
                patches = patches,
                onChange = { transform -> onSlotChange(id, transform) },
                onOpen = { onSelectSlot(id) },
            )
        }
        if (wide) {
            Row(
                Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
            ) {
                TILE_ORDER.forEach { id -> slotContent(id, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TILE_ORDER.forEach { id -> slotContent(id, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun BottomNavigation(selected: Destination, compact: Boolean, onSelect: (Destination) -> Unit) {
    HorizontalDivider(color = OkColors.Border)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Destination.entries.forEach { destination ->
            val active = selected == destination
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .background(if (active) OkColors.Primary else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(destination) })
                    .padding(horizontal = 2.dp, vertical = if (compact) 4.dp else 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (compact) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(destination.symbol, color = if (active) Color.White else OkColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
                        Text(destination.title, color = if (active) Color.White else OkColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Text(destination.symbol, color = if (active) Color.White else OkColors.TextSecondary, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(destination.title, color = if (active) Color.White else OkColors.TextSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun InstrumentDialog(
    id: VoiceSlotId,
    slot: VoiceSlot,
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    onSelect: (String, Patch) -> Unit,
    onDismiss: () -> Unit,
    onManage: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${id.label()} instrument") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose an instrument from your SoundFont library.")
                Picker(
                    label = "Instrument",
                    selected = soundFonts.firstOrNull { it.id == slot.soundFontId }?.name ?: "None",
                    options = soundFonts.flatMap { sf ->
                        patches[sf.id].orEmpty().map { p ->
                            (sf.id to p) to "${sf.name} | ${sf.instrumentName(p.bank, p.program, p.name)}"
                        }
                    },
                    onSelect = { (sf, patch) -> onSelect(sf, patch) },
                    fill = true,
                )
                if (soundFonts.isEmpty()) Text("Import a SoundFont in Settings first.", color = OkColors.TextSecondary)
                TextButton(onClick = onManage) { Text("Manage SoundFonts in Settings") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun EffectsScreen(preset: PerformancePreset, onUpdate: ((PerformancePreset) -> PerformancePreset) -> Unit) {
    Column(
        Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Effects", style = MaterialTheme.typography.titleLarge)
        OkCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SectionTitle("Master effects")
                LabeledSlider("Reverb", preset.masterReverb) { value -> onUpdate { it.copy(masterReverb = value) } }
                LabeledSlider("Chorus", preset.masterChorus) { value -> onUpdate { it.copy(masterChorus = value) } }
            }
        }
        Text("Planned: delay, EQ, and instrument-specific effects routing.", color = OkColors.TextSecondary)
    }
}

@Composable
private fun PresetLibrary(
    current: PerformancePreset,
    presets: List<PerformancePreset>,
    onRecall: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf<PerformancePreset?>(null) }
    Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Preset library", style = MaterialTheme.typography.titleLarge)
        if (presets.isEmpty()) Text("No saved presets yet.", color = OkColors.TextSecondary)
        presets.forEachIndexed { index, preset ->
            OkCard(modifier = Modifier.fillMaxWidth(), borderColor = if (preset.id == current.id) OkColors.Primary else OkColors.Border) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onRecall(preset.id) }, modifier = Modifier.weight(1f)) {
                        Text("%03d  %s".format(index + 1, preset.name), modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { deleting = preset }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete ${preset.name}", tint = OkColors.Error)
                    }
                }
            }
        }
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete preset?") },
            text = { Text("\"${preset.name}\" will be removed. The current sound settings stay loaded.") },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(preset.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SavePresetScreen(
    preset: PerformancePreset,
    presets: List<PerformancePreset>,
    onRename: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAsNew: (String) -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    val saved = presets.firstOrNull { it.id == preset.id }
    Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Save preset", style = MaterialTheme.typography.titleLarge)
        Text(if (saved == null) "Save the current setup as a preset." else "Update the selected preset or save a copy.", color = OkColors.TextSecondary)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; if (it.isNotBlank()) onRename(it) },
            label = { Text("Preset name") }, singleLine = true,
            isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSave, enabled = name.isNotBlank() && saved != preset) {
            Text(if (saved == null) "Save preset" else if (saved == preset) "Saved" else "Update preset")
        }
        OutlinedButton(onClick = { onSaveAsNew(name.trim()) }, enabled = name.isNotBlank()) { Text("Save as new") }
    }
}

@Composable
private fun Header(wide: Boolean, compact: Boolean, midi: MidiInput) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!compact || wide) {
            brand()
        } else {
            Image(painterResource(R.drawable.openkeys_mark), contentDescription = null, modifier = Modifier.height(20.dp).aspectRatio(MARK_ASPECT))
            Text("OpenKeys", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        MidiStatusChip(state, compact)
    }
    HorizontalDivider(color = OkColors.Border)
}

private const val MARK_ASPECT = 715f / 245f

@Composable
private fun MidiStatusChip(state: MidiConnectionState, compact: Boolean) {
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
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            if (compact && state == MidiConnectionState.Disconnected) "No MIDI" else label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
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
private fun SplitKeySection(splitNote: Int, onStep: (Int) -> Unit) {
    OkCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Split key")
            Stepper(MidiNote.name(splitNote), enabled = true, steps = listOf(-12, -1, 1, 12), onStep = onStep)
        }
    }
}

@Composable
private fun SoundFontSection(
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    importing: Boolean,
    onImport: () -> Unit,
    usersOf: (String) -> List<String>,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRenameInstrument: (String, Int, Int, String?) -> Unit,
    loadPatches: suspend (String) -> Boolean,
    onNavigate: () -> Unit,
) {
    var removing by remember { mutableStateOf<SoundFontEntry?>(null) }
    var editingName by remember { mutableStateOf<SoundFontEntry?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPatch by remember { mutableStateOf<Patch?>(null) }
    var loadResult by remember(selectedId) { mutableStateOf<Boolean?>(null) }
    var loadAttempt by remember(selectedId) { mutableStateOf(0) }
    var search by rememberSaveable(selectedId) { mutableStateOf("") }
    val selected = soundFonts.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) {
        selectedId = null
        onNavigate()
    }
    LaunchedEffect(selectedId, loadAttempt) {
        val id = selectedId
        if (id != null) {
            loadResult = null
            loadResult = loadPatches(id)
        }
    }
    if (selected == null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 360.dp
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Your SoundFont library",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onImport,
                        enabled = !importing,
                        shape = MaterialTheme.shapes.small,
                        modifier = if (compact) Modifier.semantics {
                            contentDescription = if (importing) "Importing SoundFont" else "Import SoundFont"
                        } else Modifier,
                    ) {
                        Text(if (importing) "Importing…" else if (compact) "Import" else "Import SoundFont")
                    }
                }
            }
            Text(
                "Choose a SoundFont to manage its name and instruments.",
                color = OkColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (soundFonts.isEmpty()) {
                OkCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No SoundFonts yet. Import an SF2 or SF3 file to start playing.",
                        modifier = Modifier.padding(16.dp),
                        color = OkColors.TextSecondary,
                    )
                }
            }
            soundFonts.forEach { sf ->
                OkCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            selectedId = sf.id
                            onNavigate()
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sf.name, style = MaterialTheme.typography.titleMedium)
                            Text("Manage SoundFont", style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = OkColors.Cyan)
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { selectedId = null; onNavigate() }) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to SoundFonts")
            }
            OkCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("SoundFont", Modifier.weight(1f))
                        IconButton(onClick = { editingName = selected }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename SoundFont")
                        }
                        IconButton(onClick = { removing = selected }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = "Remove SoundFont",
                                tint = OkColors.Error,
                            )
                        }
                    }
                    Text(selected.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Names are local to OpenKeys; the original file is not changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OkColors.TextSecondary,
                    )
                }
            }
            Text("Instruments", style = MaterialTheme.typography.titleMedium)
            Text("Select an instrument to edit its display name.", style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary)
            when (loadResult) {
                null -> Text("Loading instruments…", color = OkColors.TextSecondary)
                false -> {
                    Text("Could not load instruments from this SoundFont.", color = OkColors.Error)
                    TextButton(onClick = { loadAttempt++ }) { Text("Retry") }
                }
                true -> {
                    val instruments = patches[selected.id].orEmpty()
                    if (instruments.isEmpty()) {
                        Text("No instruments found.", color = OkColors.TextSecondary)
                    } else {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search instruments") },
                            singleLine = true,
                        )
                        val query = search.trim()
                        val matches = instruments.filter { patch ->
                            selected.instrumentName(patch.bank, patch.program, patch.name).contains(query, ignoreCase = true) ||
                                patch.name.contains(query, ignoreCase = true) ||
                                "${patch.bank}:${patch.program}".contains(query)
                        }
                        if (matches.isEmpty()) Text("No matching instruments.", color = OkColors.TextSecondary)
                        matches.forEach { patch ->
                            val name = selected.instrumentName(patch.bank, patch.program, patch.name)
                            OkCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable(role = Role.Button) { editingPatch = patch }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Bank ${patch.bank} · Program ${patch.program}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OkColors.TextSecondary,
                                        )
                                        if (name != patch.name) {
                                            Text(
                                                "Original: ${patch.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OkColors.TextMuted,
                                            )
                                        }
                                    }
                                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = OkColors.Cyan)
                                }
                            }
                        }
                    }
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
                    else "The file will be deleted from the app. Used by: ${users.joinToString()}. " +
                        "Those voices will show \"Missing SoundFont\" until you pick another.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedId == sf.id) {
                        selectedId = null
                        onNavigate()
                    }
                    removing = null
                    onRemove(sf.id)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    editingName?.let { sf ->
        RenameDialog(
            title = "Rename SoundFont",
            initialName = sf.name,
            onDismiss = { editingName = null },
            onSave = { name -> onRename(sf.id, name); editingName = null },
        )
    }
    editingPatch?.let { patch ->
        if (selected != null) {
            RenameDialog(
                title = "Rename instrument",
                initialName = selected.instrumentName(patch.bank, patch.program, patch.name),
                onDismiss = { editingPatch = null },
                onSave = { name ->
                    onRenameInstrument(selected.id, patch.bank, patch.program, name)
                    editingPatch = null
                },
                onReset = {
                    onRenameInstrument(selected.id, patch.bank, patch.program, null)
                    editingPatch = null
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This name appears in OpenKeys. The original SoundFont file stays unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OkColors.TextSecondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    singleLine = true,
                    isError = name.isBlank(),
                    supportingText = if (name.isBlank()) ({ Text("Enter a name") }) else null,
                )
                if (onReset != null) TextButton(onClick = onReset) { Text("Restore original name") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AboutSection() {
    val assets = androidx.compose.ui.platform.LocalContext.current.assets
    val files = remember { assets.list("licenses")?.sorted().orEmpty() }
    var selected by rememberSaveable { mutableStateOf(files.firstOrNull { it.startsWith("THIRD_PARTY") } ?: files.firstOrNull()) }
    val text = remember(selected) {
        selected?.let { name -> assets.open("licenses/$name").bufferedReader().use { it.readText() } }.orEmpty()
    }
    OkCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TAGLINE, color = OkColors.Cyan, style = MaterialTheme.typography.bodyMedium)
            Text("OpenKeys ${BuildConfig.VERSION_NAME}. Apache-2.0. Uses FluidSynth (LGPL-2.1) and Kshoji USB-MIDI-Driver (Apache-2.0).")
            SectionTitle("Licences")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                files.forEach { f ->
                    TextButton(onClick = { selected = f }) {
                        Text(f.substringBeforeLast('.'), style = if (f == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MidiSection(midi: MidiInput, lastEvent: MidiEvent?, onPanic: () -> Unit) {
    val state by midi.state.collectAsState()
    val devices by midi.devices.collectAsState()
    OkCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("MIDI input")
            Button(onClick = onPanic, shape = MaterialTheme.shapes.small) { Text("All notes off") }
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
            )
            if (state is MidiConnectionState.Connected) {
                OutlinedButton(onClick = midi::disconnect, shape = MaterialTheme.shapes.small) { Text("Disconnect") }
            } else if (!midi.supportsDeviceSelection) {
                OutlinedButton(onClick = { midi.connect(0) }, shape = MaterialTheme.shapes.small) { Text("Scan") }
            }
            if (midi.supportsDeviceSelection && state !is MidiConnectionState.Connected) {
                if (devices.isEmpty()) Text("No MIDI input devices found. Try the USB MIDI Driver above.", color = OkColors.TextSecondary)
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
private fun SlotCard(
    modifier: Modifier,
    label: String,
    slot: VoiceSlot,
    compact: Boolean,
    splitNote: Int?,
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    onChange: ((VoiceSlot) -> VoiceSlot) -> Unit,
    onOpen: () -> Unit,
) {
    val patch = slot.soundFontId?.let { patches[it] }.orEmpty().firstOrNull { it.bank == slot.bank && it.program == slot.program }
    val soundFont = soundFonts.firstOrNull { it.id == slot.soundFontId }
    val sfName = slot.soundFontId?.let { soundFont?.name ?: "Missing SoundFont" } ?: "No SoundFont"
    val instrumentName = patch?.let { soundFont?.instrumentName(it.bank, it.program, it.name) ?: it.name }
        ?: if (slot.soundFontId == null) "No instrument" else "Program ${slot.program}"
    val art = remember(slot.soundFontId, slot.bank, slot.program, patch?.name) {
        slot.soundFontId?.let { InstrumentCategory.of(slot.bank, slot.program, patch?.name)?.artRes() }
    }
    val active = slot.enabled
    val onOctaveStep: (Int) -> Unit = { delta ->
        onChange {
            it.copy(transposeSemitones = (it.transposeSemitones + delta).coerceIn(-VoiceSlot.MAX_TRANSPOSE, VoiceSlot.MAX_TRANSPOSE))
        }
    }
    OkCard(
        modifier = modifier,
        borderColor = if (active) OkColors.Primary else OkColors.Border,
        borderWidth = if (active) 2.dp else 1.dp,
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 4.dp else 14.dp,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (compact) 48.dp else 56.dp)
                    .toggleable(
                        value = slot.enabled,
                        role = Role.Switch,
                        onValueChange = { on -> onChange { it.copy(enabled = on) } },
                    ).semantics { contentDescription = "$label voice" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label.uppercase(),
                    modifier = Modifier.weight(1f),
                    color = if (active) OkColors.Cyan else OkColors.TextSecondary,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                splitNote?.let { note ->
                    Text(
                        MidiNote.name(note),
                        color = OkColors.Cyan,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Switch(
                    checked = slot.enabled,
                    onCheckedChange = null,
                    colors = okSwitchColors(),
                )
            }
            HorizontalDivider(color = OkColors.Border)
            if (compact) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        InstrumentPresentation(
                            instrumentName, sfName, art, active, compact = true,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onOpen = onOpen,
                        )
                        OctaveControl(slot.transposeSemitones, label, compact = true, onStep = onOctaveStep)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MixerVolumeControl(slot.volume, label, compact = true) { value ->
                            onChange { it.copy(volume = value) }
                        }
                    }
                }
            } else {
                InstrumentPresentation(
                    instrumentName, sfName, art, active, compact = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onOpen = onOpen,
                )
                MixerVolumeControl(slot.volume, label, compact = false) { value ->
                    onChange { it.copy(volume = value) }
                }
                OctaveControl(
                    slot.transposeSemitones, label, compact = false,
                    modifier = Modifier.padding(top = 8.dp), onStep = onOctaveStep,
                )
            }
        }
    }
}

@Composable
private fun OctaveControl(
    semitones: Int,
    slotLabel: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onStep: (Int) -> Unit,
) {
    val octaves = semitones / 12
    val rest = semitones % 12
    Surface(
        modifier = modifier.widthIn(max = 220.dp),
        shape = RoundedCornerShape(50),
        color = OkColors.Elevated,
        border = BorderStroke(1.dp, OkColors.Border),
    ) {
        val buttonSize = if (compact) 36.dp else 44.dp
        @Composable
        fun StepButton(symbol: String, delta: Int, description: String) {
            val enabled = (semitones + delta) in -VoiceSlot.MAX_TRANSPOSE..VoiceSlot.MAX_TRANSPOSE
            Box(
                Modifier.padding(3.dp).size(buttonSize).clip(CircleShape)
                    .background(if (enabled) OkColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Button) { onStep(delta) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) OkColors.Cyan else OkColors.TextMuted,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", -12, "$slotLabel octave down")
            Text(
                if (rest == 0) "Oct %+d".format(octaves) else "Oct %+d %+dst".format(octaves, rest),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$slotLabel octave: $semitones semitones" },
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (semitones != 0) OkColors.Cyan else OkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton("+", 12, "$slotLabel octave up")
        }
    }
}

@Composable
private fun MixerVolumeControl(value: Float, slotLabel: String, compact: Boolean, onChange: (Float) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = OkColors.Elevated,
        border = BorderStroke(1.dp, OkColors.Border),
    ) {
        Column(Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (compact) "Vol" else "Volume", style = MaterialTheme.typography.labelSmall, color = OkColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${(value * 100).toInt()}%", color = OkColors.Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = OkColors.TextPrimary,
                    activeTrackColor = OkColors.Cyan,
                    inactiveTrackColor = OkColors.Border,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$slotLabel volume" },
            )
        }
    }
}

@Composable
private fun InstrumentPresentation(
    instrumentName: String,
    soundFontName: String,
    @DrawableRes art: Int?,
    active: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    Box(
        modifier.clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Select instrument: $instrumentName" },
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = 1.6f
                        scaleY = 1.6f
                        translationX = size.width * 0.10f
                    }
                    .alpha(if (active) 1f else 0.5f),
            )
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to OkColors.Card,
                0.4f to OkColors.Card.copy(alpha = 0.75f),
                0.9f to Color.Transparent,
                1f to OkColors.Card.copy(alpha = 0.25f),
            )))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to OkColors.Card.copy(alpha = 0.5f),
                0.2f to Color.Transparent,
                0.8f to Color.Transparent,
                1f to OkColors.Card.copy(alpha = 0.65f),
            )))
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.85f)) {
            Text(
                instrumentName,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OkColors.TextPrimary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(soundFontName, style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun <T> Picker(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
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
        steps.filter { it < 0 }.forEach { step ->
            IconButton(onClick = { onStep(step) }, enabled = enabled, modifier = Modifier.size(44.dp)) { Text("$step") }
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        steps.filter { it > 0 }.forEach { step ->
            IconButton(onClick = { onStep(step) }, enabled = enabled, modifier = Modifier.size(44.dp)) { Text("+$step") }
        }
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
private fun SettingsScreen(
    midi: MidiInput,
    lastEvent: MidiEvent?,
    onPanic: () -> Unit,
    driver: MidiDriverType,
    onSelect: (MidiDriverType) -> Unit,
    echo: Boolean,
    onEcho: (Boolean) -> Unit,
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    importing: Boolean,
    onImport: () -> Unit,
    usersOf: (String) -> List<String>,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRenameInstrument: (String, Int, Int, String?) -> Unit,
    loadPatches: suspend (String) -> Boolean,
    preset: PerformancePreset,
    transpose: Int,
    onTranspose: (Int) -> Unit,
    onUpdate: ((PerformancePreset) -> PerformancePreset) -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val tabs = listOf("Performance", "Input", "Output", "Sound", "About")
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 500.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(if (narrow) 100.dp else 168.dp).fillMaxHeight()
                    .background(OkColors.Card).verticalScroll(rememberScrollState())
                    .selectableGroup().padding(horizontal = 6.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .background(if (selected) OkColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(index) })
                            .padding(horizontal = if (narrow) 6.dp else 12.dp, vertical = 14.dp),
                    ) {
                        Text(title, color = if (selected) OkColors.Cyan else OkColors.TextSecondary,
                            style = if (narrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                key(selectedTab) {
                    val contentScroll = rememberScrollState()
                    Column(
                        Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight()
                            .verticalScroll(contentScroll).padding(if (narrow) 10.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Settings · ${tabs[selectedTab]}", style = MaterialTheme.typography.titleLarge)
                        when (selectedTab) {
                            0 -> {
                                OkCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        SectionTitle("Global transpose")
                                        Text("Applies to all presets, after keyboard zone selection.", color = OkColors.TextSecondary)
                                        Stepper("%+d semitones".format(transpose), true, listOf(-1, 1)) {
                                            onTranspose((transpose + it).coerceIn(-12, 12))
                                        }
                                    }
                                }
                                SplitKeySection(preset.splitNote) { delta ->
                                    onUpdate {
                                        it.copy(splitNote = (it.splitNote + delta).coerceIn(MidiNote.MIN, MidiNote.MAX))
                                    }
                                }
                                Text("Audio engine: FluidSynth with low-latency Oboe output. Audio configuration is automatic.", color = OkColors.TextSecondary)
                            }
                            1 -> {
                                OkCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        SectionTitle("MIDI input driver")
                                        DriverOption(driver == MidiDriverType.ANDROID_MIDI, "Android MIDI", "Standard class-compliant USB MIDI devices.") { onSelect(MidiDriverType.ANDROID_MIDI) }
                                        DriverOption(driver == MidiDriverType.USB_MIDI_DRIVER, "USB MIDI Driver (Kshoji)", "Direct USB access for supported keyboards Android does not list.") { onSelect(MidiDriverType.USB_MIDI_DRIVER) }
                                    }
                                }
                                MidiSection(midi, lastEvent, onPanic)
                                Text("MIDI input accepts all channels. Slot output channels are fixed: Right 1: 1, Right 2: 2, Left 1: 3.", color = OkColors.TextSecondary)
                            }
                            2 -> OkCard(modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Echo routed notes to keyboard", style = MaterialTheme.typography.titleSmall)
                                        Text("Sends routed notes, pedals, program and volume to the keyboard. Set Local Control off to avoid doubled notes.", style = MaterialTheme.typography.bodySmall, color = OkColors.TextSecondary)
                                    }
                                    Switch(checked = echo, onCheckedChange = onEcho, colors = okSwitchColors())
                                }
                            }
                            3 -> SoundFontSection(
                                soundFonts = soundFonts, patches = patches, importing = importing, onImport = onImport,
                                usersOf = usersOf, onRemove = onRemove, onRename = onRename,
                                onRenameInstrument = onRenameInstrument, loadPatches = loadPatches,
                                onNavigate = { scope.launch { contentScroll.scrollTo(0) } },
                            )
                            4 -> AboutSection()
                        }
                    }
                }
            }
        }
    }
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

private fun describe(event: MidiEvent): String = when (event) {
    is MidiEvent.NoteOn -> "Note on ${MidiNote.name(event.note)} vel ${event.velocity} ch ${event.channel + 1}"
    is MidiEvent.NoteOff -> "Note off ${MidiNote.name(event.note)} ch ${event.channel + 1}"
    is MidiEvent.ControlChange -> "CC ${event.controller} = ${event.value} ch ${event.channel + 1}"
    is MidiEvent.PitchBend -> "Pitch bend ${event.value} ch ${event.channel + 1}"
}
