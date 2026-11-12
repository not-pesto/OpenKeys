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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
                    TILE_ORDER.forEach { id ->
                        SlotCard(
                            modifier = mod,
                            label = id.label(),
                            slot = preset.slot(id),
                            inactive = id == VoiceSlotId.LEFT_1 && !preset.splitEnabled,
                            compact = compact,
                            soundFonts = soundFonts,
                            patches = patches,
                            onChange = { transform -> perf.updateSlot(id, transform) },
                            onSelectInstrument = { sf, patch -> perf.selectInstrument(id, sf, patch) },
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

        }
    }

    if (showSettings) {
        SettingsDialog(
            midi = midi,
            lastEvent = lastEvent,
            onPanic = perf::panic,
            driver = driver,
            onSelect = app::setMidiDriver,
            echo = echo,
            onEcho = app::setEchoToKeyboard,
            soundFonts = soundFonts,
            patches = patches,
            importing = importing,
            onImport = { pickSoundFont.launch(arrayOf("*/*")) },
            usersOf = perf::soundFontUsers,
            onRemove = perf::removeSoundFont,
            onRename = perf::renameSoundFont,
            onRenameInstrument = perf::renameInstrument,
            loadPatches = perf::loadSoundFontPatches,
            message = message,
            onDismissMessage = perf::dismissMessage,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Header(wide: Boolean, compact: Boolean, midi: MidiInput, onSettings: () -> Unit) {
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
            OutlinedButton(onClick = onSettings, shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Settings")
            }
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
    patches: Map<String, List<Patch>>,
    onChange: ((VoiceSlot) -> VoiceSlot) -> Unit,
    onSelectInstrument: (String, Patch) -> Unit,
) {
    val patch = slot.soundFontId?.let { patches[it] }.orEmpty().firstOrNull { it.bank == slot.bank && it.program == slot.program }
    val soundFont = soundFonts.firstOrNull { it.id == slot.soundFontId }
    val sfName = slot.soundFontId?.let { soundFont?.name ?: "Missing SoundFont" } ?: "No SoundFont"
    val instrumentName = patch?.let { soundFont?.instrumentName(it.bank, it.program, it.name) ?: it.name }
        ?: if (slot.soundFontId == null) "No instrument" else "Program ${slot.program}"
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
            BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (active) 1f else 0.6f)) {
                val artSize = (maxWidth * if (compact) 0.5f else 0.62f).coerceIn(110.dp, 240.dp)
                Box(Modifier.fillMaxWidth().height(artSize * 0.8f)) {
                    if (art != null) {
                        InstrumentImage(
                            art,
                            Modifier.align(Alignment.CenterEnd).offset(x = 14.dp).size(artSize),
                        )
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.horizontalGradient(
                                    0f to OkColors.Card,
                                    0.3f to OkColors.Card.copy(alpha = 0.85f),
                                    0.65f to Color.Transparent,
                                ),
                            ),
                        )
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(0.7f to Color.Transparent, 1f to OkColors.Card),
                            ),
                        )
                    }
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(if (art != null) 0.6f else 1f)) {
                        Text(
                            instrumentName,
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
                }
            }
            Picker(
                label = "Instrument",
                selected = if (slot.soundFontId == null) instrumentName else "$sfName | $instrumentName",
                options = soundFonts.flatMap { sf ->
                    patches[sf.id].orEmpty().map { p ->
                        (sf.id to p) to "${sf.name} | ${sf.instrumentName(p.bank, p.program, p.name)}"
                    }
                },
                onSelect = { (id, selectedPatch) -> onSelectInstrument(id, selectedPatch) },
                fill = true,
            )
            HorizontalDivider(color = OkColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vol ${(slot.volume * 100).toInt()}%", color = OkColors.TextSecondary, modifier = Modifier.width(72.dp))
                Slider(
                    value = slot.volume,
                    onValueChange = { v -> onChange { it.copy(volume = v) } },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OctaveControl(slot.transposeSemitones) { d ->
                    onChange {
                        it.copy(transposeSemitones = (it.transposeSemitones + d).coerceIn(-VoiceSlot.MAX_TRANSPOSE, VoiceSlot.MAX_TRANSPOSE))
                    }
                }
            }
        }
    }
}

@Composable
private fun OctaveControl(semitones: Int, onStep: (Int) -> Unit) {
    val octaves = semitones / 12
    val rest = semitones % 12
    Row(
        Modifier.border(1.dp, OkColors.Border, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onStep(-12) }, enabled = semitones - 12 >= -VoiceSlot.MAX_TRANSPOSE) { Text("−") }
        Text(
            if (rest == 0) "Oct %+d".format(octaves) else "Oct %+d %+dst".format(octaves, rest),
            style = MaterialTheme.typography.labelLarge,
            color = if (semitones == 0) OkColors.TextSecondary else OkColors.Cyan,
        )
        TextButton(onClick = { onStep(12) }, enabled = semitones + 12 <= VoiceSlot.MAX_TRANSPOSE) { Text("+") }
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
    message: String?,
    onDismissMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Input", "Output", "Sound", "About")
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 850.dp).fillMaxWidth(0.96f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            color = OkColors.Background,
            border = BorderStroke(1.dp, OkColors.Border),
        ) {
            BoxWithConstraints {
                val compact = maxWidth < 500.dp
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                    HorizontalDivider(color = OkColors.Border)
                    Row(Modifier.weight(1f)) {
                        Column(
                            Modifier.width(if (compact) 100.dp else 160.dp).fillMaxHeight()
                                .background(OkColors.Card)
                                .verticalScroll(rememberScrollState())
                                .selectableGroup()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val selected = selectedTab == index
                                Box(
                                    Modifier.fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (selected) OkColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                                        .selectable(selected = selected, role = Role.Tab, onClick = { selectedTab = index })
                                        .padding(horizontal = 10.dp, vertical = 14.dp),
                                ) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) OkColors.Cyan else OkColors.TextSecondary,
                                    )
                                }
                            }
                        }
                        key(selectedTab) {
                            val contentScroll = rememberScrollState()
                            Column(
                                Modifier.weight(1f).fillMaxHeight().verticalScroll(contentScroll)
                                    .padding(if (compact) 12.dp else 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(tabs[selectedTab], style = MaterialTheme.typography.titleLarge)
                                message?.let {
                                    OkCard(borderColor = OkColors.Error) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(it, color = OkColors.Error)
                                            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                                        }
                                    }
                                }
                                when (selectedTab) {
                                    0 -> {
                                        OkCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(14.dp)) {
                                                SectionTitle("MIDI input driver")
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
                                            }
                                        }
                                        MidiSection(midi, lastEvent, onPanic)
                                    }
                                    1 -> OkCard {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("Echo routed notes to keyboard", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Sends split/octave-routed notes, pedals, program and volume back to the keyboard " +
                                                        "(Left 1 → ch 3, Right 1 → ch 1, Right 2 → ch 2). Set Local Control off on the " +
                                                        "keyboard to avoid doubled notes.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = OkColors.TextSecondary,
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Switch(checked = echo, onCheckedChange = onEcho, colors = okSwitchColors())
                                        }
                                    }
                                    2 -> SoundFontSection(
                                        soundFonts = soundFonts,
                                        patches = patches,
                                        importing = importing,
                                        onImport = onImport,
                                        usersOf = usersOf,
                                        onRemove = onRemove,
                                        onRename = onRename,
                                        onRenameInstrument = onRenameInstrument,
                                        loadPatches = loadPatches,
                                        onNavigate = { scope.launch { contentScroll.scrollTo(0) } },
                                    )
                                    3 -> AboutSection()
                                }
                            }
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
