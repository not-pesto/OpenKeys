package io.github.openkeys.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.openkeys.R
import io.github.openkeys.audio.Patch
import io.github.openkeys.instruments.BrowserInstrument
import io.github.openkeys.instruments.VoiceCategory
import io.github.openkeys.instruments.inCategory
import io.github.openkeys.instruments.instrumentCatalog
import io.github.openkeys.presets.VoiceSlot
import io.github.openkeys.routing.VoiceSlotId
import io.github.openkeys.soundfonts.SoundFontEntry
import io.github.openkeys.ui.theme.OkColors

@Composable
fun InstrumentDialog(
    id: VoiceSlotId,
    slot: VoiceSlot,
    soundFonts: List<SoundFontEntry>,
    patches: Map<String, List<Patch>>,
    onSelect: (String, Patch) -> Unit,
    onDismiss: () -> Unit,
) {
    val catalog = remember(soundFonts, patches) { instrumentCatalog(soundFonts, patches) }
    val assignedCategory = catalog.firstOrNull { it.isAssignedTo(slot) }?.category
    // A catalog arriving after opening must still reveal the assigned voice.
    var category by rememberSaveable(id, assignedCategory) {
        mutableStateOf(assignedCategory ?: VoiceCategory.PIANO)
    }
    var search by rememberSaveable(id, category) { mutableStateOf("") }
    val visible = remember(catalog, category, search) { catalog.inCategory(category, search) }
    val midpoint = (visible.size + 1) / 2
    val slotName = id.name.replace('_', ' ')
    val shape = RoundedCornerShape(10.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 1100.dp).heightIn(max = 680.dp)
                .fillMaxWidth(0.94f).fillMaxHeight(0.92f),
            shape = shape,
            color = OkColors.Background,
            border = BorderStroke(1.dp, OkColors.Border),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 700.dp
                val sidebarWidth = if (compact) (maxWidth * 0.30f).coerceIn(104.dp, 180.dp)
                    else (maxWidth * 0.23f).coerceIn(196.dp, 240.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(OkColors.Card).padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_keyboard), null, Modifier.size(24.dp), tint = Color.Unspecified)
                        Text(
                            "Select Instrument \u2013 $slotName",
                            Modifier.weight(1f),
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            color = OkColors.TextPrimary,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), "Close instrument selector", tint = OkColors.TextPrimary)
                        }
                    }
                    HorizontalDivider(color = OkColors.Border)
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategorySidebar(category, sidebarWidth, compact) { category = it }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (compact) {
                                Text(category.title, color = OkColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                                CategorySearch(category, search, { search = it }, Modifier.fillMaxWidth())
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(category.title, Modifier.weight(0.85f), color = OkColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                                    CategorySearch(category, search, { search = it }, Modifier.weight(1.15f))
                                }
                            }
                            HorizontalDivider(color = OkColors.Border)
                            key(category, search) {
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InstrumentColumn(visible.take(midpoint), slot, compact, Modifier.weight(1f), onSelect)
                                    InstrumentColumn(visible.drop(midpoint), slot, compact, Modifier.weight(1f), onSelect)
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
private fun CategorySearch(category: VoiceCategory, search: String, onSearch: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = search,
        onValueChange = onSearch,
        modifier = modifier.semantics { contentDescription = "Search instruments in ${category.title}" },
        placeholder = { Text("Search in ${category.title}...", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(22.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun CategorySidebar(selected: VoiceCategory, width: androidx.compose.ui.unit.Dp, compact: Boolean, onSelect: (VoiceCategory) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal)
    LazyColumn(
        Modifier.width(width).heightIn(max = if (compact) 596.dp else 548.dp).fillMaxHeight()
            .border(1.dp, OkColors.Border, shape).clip(shape)
            .background(OkColors.Card).selectableGroup().padding(4.dp),
        state = state,
    ) {
        items(VoiceCategory.entries, key = { it.name }) { category ->
            val active = category == selected
            Row(
                Modifier.fillMaxWidth().height(if (compact) 48.dp else 44.dp)
                    .clip(shape).background(if (active) OkColors.Primary else Color.Transparent)
                    .border(1.dp, if (active) OkColors.Cyan else Color.Transparent, shape)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(category) })
                    .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                Icon(painterResource(category.iconRes()), null, Modifier.size(if (compact) 18.dp else 22.dp), tint = if (active) Color.White else OkColors.TextSecondary)
                Text(
                    category.title,
                    color = OkColors.TextPrimary,
                    style = if (compact) MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp) else MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 6.dp), color = OkColors.Border)
        }
    }
}

@Composable
private fun InstrumentColumn(
    instruments: List<BrowserInstrument>,
    slot: VoiceSlot,
    compact: Boolean,
    modifier: Modifier,
    onSelect: (String, Patch) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val assignedIndex = instruments.indexOfFirst { it.isAssignedTo(slot) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = assignedIndex.coerceAtLeast(0))
    LaunchedEffect(instruments) {
        if (assignedIndex >= 0) state.scrollToItem(assignedIndex)
    }
    LazyColumn(
        modifier.fillMaxHeight().border(1.dp, OkColors.Border, shape)
            .clip(shape).selectableGroup().padding(4.dp),
        state = state,
    ) {
        items(instruments, key = { it.key }) { instrument ->
            val active = instrument.isAssignedTo(slot)
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (compact) 60.dp else 64.dp)
                    .clip(shape).background(if (active) OkColors.Primary else Color.Transparent)
                    .border(1.dp, if (active) OkColors.Cyan else Color.Transparent, shape)
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(instrument.soundFont.id, instrument.patch) })
                    .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(instrument.name, color = OkColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
                Text(instrument.soundFont.sourceFileName, color = if (active) Color(0xFFD3E5F7) else OkColors.TextSecondary, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(Modifier.padding(horizontal = 2.dp), color = OkColors.Border)
        }
    }
}

// The mixer resources are full-colour artwork; use standard monochrome icons here instead.
@DrawableRes
private fun VoiceCategory.iconRes(): Int = when (this) {
    VoiceCategory.PIANO -> R.drawable.ic_keyboard
    VoiceCategory.ORGAN -> android.R.drawable.ic_menu_sort_by_size
    VoiceCategory.GUITAR, VoiceCategory.BASS, VoiceCategory.STRINGS,
    VoiceCategory.BRASS, VoiceCategory.WOODWIND -> android.R.drawable.ic_media_play
    VoiceCategory.ACCORDION -> android.R.drawable.ic_menu_agenda
    VoiceCategory.CHOIR_PAD -> android.R.drawable.ic_menu_myplaces
    VoiceCategory.SYNTH -> R.drawable.ic_settings
    VoiceCategory.PERCUSSION -> android.R.drawable.ic_menu_sort_by_size
    VoiceCategory.USER -> android.R.drawable.ic_menu_add
}
