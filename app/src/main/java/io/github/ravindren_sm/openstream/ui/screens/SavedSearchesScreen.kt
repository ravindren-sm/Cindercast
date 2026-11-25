package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.BrowseSort
import io.github.ravindren_sm.openstream.data.SavedKind
import io.github.ravindren_sm.openstream.data.SavedSearch
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.OsTextField
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.focus.enterAt
import io.github.ravindren_sm.openstream.ui.focus.trapFocus
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

fun savedRailFocusId(i: Int) = "saved-rail-$i"
const val SAVED_NEW_ID = "saved-new"
const val SAVED_EDIT_ID = "saved-edit"

/** What the Saved tab shows besides the list of searches: the results of the selected one. */
class SavedResults(val items: List<VodItem>, val total: Int)

/**
 * Saved searches: named searches over the on-device title index (genres, languages, a minimum rating, a year, a sort),
 * listed on the left; Select runs one and shows its titles as a poster grid. Edit changes it, "+ New search" makes another.
 * Needs the title index (Settings builds it); without it the screen says so.
 */
@Composable
fun SavedSearchesScreen(
    searches: List<SavedSearch>,
    selectedId: String?,
    results: SavedResults?,
    loading: Boolean,
    indexReady: Boolean,
    portalUrl: String,
    memory: FocusMemory,
    isFavorite: (VodItem) -> Boolean,
    onSelect: (SavedSearch) -> Unit,
    onOpen: (VodItem) -> Unit,
    onSave: (SavedSearch) -> Unit,
    onDelete: (SavedSearch) -> Unit,
    newId: () -> String,
    modifier: Modifier = Modifier,
) {
    var editing: SavedSearch? by remember { mutableStateOf(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = searches.firstOrNull { it.id == selectedId }

    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().enterAt(memory, setOf(androidx.compose.ui.focus.FocusDirection.Down)) { savedRailFocusId(searches.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)).takeIf { searches.isNotEmpty() } ?: SAVED_NEW_ID }
                .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
        ) {
            Column(Modifier.width(440.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                Text("Saved searches", style = Tokens.TitleMedium, color = Tokens.TextPrimary, modifier = Modifier.padding(bottom = Spacing.S1))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S1), modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(searches, key = { _, s -> s.id }) { i, s ->
                        OsFocusable(id = savedRailFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onSelect(s) }) { _ ->
                            Text(
                                s.name, style = Tokens.BodyLarge, maxLines = 1, color = if (s.id == selectedId) Tokens.AccentPrimary else Tokens.TextPrimary,
                                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp),
                            )
                        }
                    }
                }
                OsFocusable(id = SAVED_NEW_ID, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { creating = true }) { _ ->
                    Text("+  New search", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                when {
                    !indexReady -> Text(
                        "Saved searches use the title index on this device, which is not built yet. Build it in Settings (Title index), then come back.",
                        style = Tokens.BodyLarge, color = Tokens.TextSecondary,
                    )
                    searches.isEmpty() -> Text(
                        "No saved searches yet. Choose \"+ New search\" to save one, for example comedy or sci-fi in English with a rating of 6 or more.",
                        style = Tokens.BodyLarge, color = Tokens.TextSecondary,
                    )
                    selected == null -> Text("Choose a search on the left.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                    else -> SavedResultsPane(selected, results, loading, portalUrl, memory, isFavorite, onOpen, onEdit = { editing = selected })
                }
            }
        }
        if (creating) SavedSearchEditor(
            initial = SavedSearch(id = newId(), name = ""), isNew = true, memory = memory,
            onSave = { onSave(it); creating = false }, onCancel = { creating = false }, onDelete = {},
        )
        editing?.let { e ->
            SavedSearchEditor(
                initial = e, isNew = false, memory = memory,
                onSave = { onSave(it); editing = null }, onCancel = { editing = null }, onDelete = { onDelete(e); editing = null },
            )
        }
    }
}

@Composable
private fun SavedResultsPane(
    search: SavedSearch, results: SavedResults?, loading: Boolean, portalUrl: String, memory: FocusMemory,
    isFavorite: (VodItem) -> Boolean, onOpen: (VodItem) -> Unit, onEdit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(search.name, style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 1)
            Text(
                search.summary() + when {
                    loading -> "  ·  searching…"
                    results != null -> "  ·  %,d titles".format(results.total) + if (results.total > results.items.size) " (first %,d shown)".format(results.items.size) else ""
                    else -> ""
                },
                style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2,
            )
        }
        OsFocusable(id = SAVED_EDIT_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onEdit) { _ ->
            Text("Edit", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
        }
    }
    when {
        results == null -> Text(if (loading) "Searching the title index…" else "", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        results.items.isEmpty() -> Text(
            "Nothing in the index matches. Titles without online tags have no genre or language to match; try fewer choices.",
            style = Tokens.BodyLarge, color = Tokens.TextSecondary,
        )
        else -> {
            val gridState = remember(search.id) { LazyGridState() }
            val scope = rememberCoroutineScope()
            val pending = remember { GridMove() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS), state = gridState, contentPadding = Spacing.CardRow,
                modifier = Modifier.fillMaxSize().enterAt(memory) { titleFocusId(gridState.firstVisibleItemIndex) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2),
            ) {
                itemsIndexed(results.items, key = { _, t -> t.id }) { index, item ->
                    TitleCard(
                        index, item, portalUrl, memory, { _, it -> onOpen(it) }, isFavorite(item),
                        onVertical = { from, down -> moveInGrid(from, down, results.items.lastIndex, gridState, memory, scope, pending) },
                    )
                }
            }
        }
    }
}

private val SAVED_GENRES = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western")
private val SAVED_LANGUAGES = listOf("en" to "English", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali", "pa" to "Punjabi", "mr" to "Marathi",
    "ko" to "Korean", "ja" to "Japanese", "zh" to "Chinese", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "tr" to "Turkish", "ar" to "Arabic")
private val SAVED_RATINGS = listOf(5.0, 6.0, 7.0, 8.0)
private val SAVED_YEARS = listOf(2020, 2010, 2000, 1990, 1980)

private enum class Picker { KIND, GENRES, LANGUAGES, RATING, YEAR, SORT }

/** The form for one saved search: every choice is a row; Select opens its picker. Back cancels. */
@Composable
private fun SavedSearchEditor(initial: SavedSearch, isNew: Boolean, memory: FocusMemory, onSave: (SavedSearch) -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    var s by remember { mutableStateOf(initial) }
    var picker: Picker? by remember { mutableStateOf(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = picker == null && !confirmDelete) { onCancel() }
    LaunchedEffect(Unit) { withFrameNanos { }; memory.tryFocus("ss-name") }

    Box(Modifier.fillMaxSize().trapFocus().background(Tokens.BgCanvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Text(if (isNew) "New saved search" else "Edit saved search", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
            OsTextField(
                id = "ss-name", memory = memory, value = s.name, onValueChange = { s = s.copy(name = it) }, label = "Name",
                hint = "Leave empty for an automatic name from the choices below.", downId = "ss-kind",
            )
            @Composable fun Row_(id: String, title: String, value: String, p: Picker) {
                OsFocusable(id = id, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { picker = p }) { _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.width(420.dp))
                        Text(value, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("Change", style = Tokens.Body, color = Tokens.AccentPrimary)
                    }
                }
            }
            Row_("ss-kind", "Look in", when (s.kind) { SavedKind.BOTH -> "Movies and shows"; SavedKind.MOVIES -> "Movies"; SavedKind.SHOWS -> "Shows" }, Picker.KIND)
            Row_("ss-genres", "Genres", if (s.genres.isEmpty()) "Any" else s.genres.sorted().joinToString(if (s.allGenres) " + " else " / "), Picker.GENRES)
            OsFocusable(id = "ss-match", memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { s = s.copy(allGenres = !s.allGenres) }) { _ ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                    Text("Several genres", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.width(420.dp))
                    Text(if (s.allGenres) "A title must have all of them" else "A title may have any of them", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("Switch", style = Tokens.Body, color = Tokens.AccentPrimary)
                }
            }
            Row_("ss-lang", "Languages", if (s.languages.isEmpty()) "Any" else SAVED_LANGUAGES.filter { it.first in s.languages }.joinToString(" / ") { it.second }, Picker.LANGUAGES)
            Row_("ss-rating", "Rating of at least", s.minRating?.let { "%.0f".format(it) } ?: "Any", Picker.RATING)
            Row_("ss-year", "Released since", s.yearFrom?.toString() ?: "Any year", Picker.YEAR)
            Row_("ss-sort", "Order", s.sort.label, Picker.SORT)
            Text("Genre, language and rating come from the online tags and the portal's lists; titles without them cannot match those choices.", style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.padding(vertical = Spacing.S1))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                OsFocusable(id = "ss-save", memory = memory, variant = FocusVariant.BUTTON, onSelect = { onSave(s.copy(name = s.name.trim().ifBlank { s.autoName() })) }) { _ ->
                    Text("Save", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
                OsFocusable(id = "ss-cancel", memory = memory, variant = FocusVariant.BUTTON, onSelect = onCancel) { _ ->
                    Text("Cancel", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
                if (!isNew) OsFocusable(id = "ss-delete", memory = memory, variant = FocusVariant.BUTTON, onSelect = { confirmDelete = true }) { _ ->
                    Text("Delete this search", style = Tokens.BodyLarge, color = Tokens.StatusError, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
            }
        }
        when (picker) {
            Picker.KIND -> ChoicePicker("Look in", listOf(Choice("both", "Movies and shows"), Choice("movies", "Movies"), Choice("shows", "Shows")), setOf(s.kind.name.lowercase()), multi = false, note = null,
                memory = memory, returnFocusId = "ss-kind", onToggle = { id -> s = s.copy(kind = SavedKind.valueOf(id.uppercase())) }, onClose = { picker = null })
            Picker.GENRES -> ChoicePicker("Genres", SAVED_GENRES.map { Choice(it, it) }, s.genres, multi = true, note = "Pick up to four. The switch below decides whether a title needs all of them or any.",
                memory = memory, returnFocusId = "ss-genres", clearLabel = "Any genre", onClear = { s = s.copy(genres = emptySet()) },
                onToggle = { id -> s = s.copy(genres = if (id in s.genres) s.genres - id else if (s.genres.size < 4) s.genres + id else s.genres) }, onClose = { picker = null })
            Picker.LANGUAGES -> ChoicePicker("Languages", SAVED_LANGUAGES.map { Choice(it.first, it.second) }, s.languages, multi = true, note = "The language the title was made in (from the online tags). Pick up to four.",
                memory = memory, returnFocusId = "ss-lang", clearLabel = "Any language", onClear = { s = s.copy(languages = emptySet()) },
                onToggle = { id -> s = s.copy(languages = if (id in s.languages) s.languages - id else if (s.languages.size < 4) s.languages + id else s.languages) }, onClose = { picker = null })
            Picker.RATING -> ChoicePicker("Rating of at least", listOf(Choice("any", "Any rating")) + SAVED_RATINGS.map { Choice("$it", "%.0f".format(it)) }, setOf(s.minRating?.toString() ?: "any"), multi = false,
                note = "IMDb when the title has one, else Kinopoisk, else the online tags' score. Titles with no rating are left out once a minimum is set.",
                memory = memory, returnFocusId = "ss-rating", onToggle = { id -> s = s.copy(minRating = id.toDoubleOrNull()) }, onClose = { picker = null })
            Picker.YEAR -> ChoicePicker("Released since", listOf(Choice("any", "Any year")) + SAVED_YEARS.map { Choice("$it", "$it") }, setOf(s.yearFrom?.toString() ?: "any"), multi = false, note = null,
                memory = memory, returnFocusId = "ss-year", onToggle = { id -> s = s.copy(yearFrom = id.toIntOrNull()) }, onClose = { picker = null })
            Picker.SORT -> ChoicePicker("Order", BrowseSort.entries.map { Choice(it.name, it.label) }, setOf(s.sort.name), multi = false, note = null,
                memory = memory, returnFocusId = "ss-sort", onToggle = { id -> s = s.copy(sort = BrowseSort.valueOf(id)) }, onClose = { picker = null })
            null -> Unit
        }
        if (confirmDelete) ChoicePicker("Delete \"${s.name.ifBlank { s.autoName() }}\"?", listOf(Choice("yes", "Yes, delete"), Choice("no", "Cancel")), emptySet(), multi = false, note = null,
            memory = memory, returnFocusId = "ss-delete", onToggle = { id -> if (id == "yes") onDelete() }, onClose = { confirmDelete = false })
    }
}
