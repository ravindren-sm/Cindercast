package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.profiles.ParentalPolicy
import io.github.ravindren_sm.openstream.profiles.ProfileInfo
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.OsTextField
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** The colours a profile tile can have. */
val PROFILE_COLORS = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899))

/** What the edit form hands back. [id] is 0 for a new profile. */
class ProfileDraft(val id: Long, val name: String, val isKids: Boolean, val maxAgeLevel: Int, val showAdult: Boolean, val colorIndex: Int)

/** The highest age a Kids profile may watch, as the choices an adult sees. G and PG is the default. */
private val AGE_CHOICES = listOf(Choice("0", "G (all ages)"), Choice("${ParentalPolicy.DEFAULT_KIDS_MAX_AGE}", "G and PG"), Choice("13", "Up to PG-13"))

private fun profileFocusId(i: Int) = "profile-$i"
private const val ADD_ID = "profile-add"
private const val MANAGE_ID = "profile-manage"
private const val CLOSE_ID = "profile-close"

/** What the profiles screens need from the shell. The shell owns storage, the PIN checks and the portal. */
class ProfilesModel(
    val profiles: List<ProfileInfo>,
    val currentId: Long,
    val pinIsSet: Boolean,
    /** Null at launch when a choice is required; otherwise leaving is allowed. */
    val onClose: (() -> Unit)?,
    val onChoose: (ProfileInfo) -> Unit,
    val onSave: (ProfileDraft) -> Unit,
    val onDelete: (ProfileInfo) -> Unit,
    val onChangePin: () -> Unit,
    /** Removing the PIN is offered only while no Kids profile exists (a Kids profile is only locked by the PIN). */
    val onRemovePin: () -> Unit,
    /** Runs the given step once the shell has checked the PIN (when one is set): managing profiles is for adults. */
    val guardManage: (proceed: () -> Unit) -> Unit,
    /** The VOD categories that can be allowed for Kids (adult ones left out), and the ones allowed for the profile being edited. */
    val kidsChoices: List<Choice>,
    val kidsAllowed: Set<String>,
    val onOpenKidsCategories: (ProfileInfo) -> Unit,
    val onToggleKidsCategory: (ProfileInfo, String) -> Unit,
)

private enum class Mode { PICK, MANAGE, EDIT }

/**
 * Who is watching: a tile per profile (Select to start as that profile), Manage to add, edit or delete, and the household PIN.
 * The shell checks the PIN before Manage opens, so the screen itself holds no security logic.
 */
@Composable
fun ProfilesScreen(model: ProfilesModel, memory: FocusMemory, startInManage: Boolean = false, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(if (startInManage) Mode.MANAGE else Mode.PICK) }
    var editing by remember { mutableStateOf<ProfileInfo?>(null) }           // null while adding
    var confirmDelete by remember { mutableStateOf<ProfileInfo?>(null) }
    var kidsPicker by remember { mutableStateOf<ProfileInfo?>(null) }

    fun back() { when { mode == Mode.EDIT -> mode = Mode.MANAGE; mode == Mode.MANAGE && !startInManage -> mode = Mode.PICK; else -> model.onClose?.invoke() } }
    BackHandler(enabled = kidsPicker == null && confirmDelete == null && (mode != Mode.PICK || model.onClose != null)) { back() }
    LaunchedEffect(mode) {
        withFrameNanos { }
        memory.tryFocus(when (mode) {
            Mode.PICK -> profileFocusId(model.profiles.indexOfFirst { it.id == model.currentId }.coerceAtLeast(0))
            Mode.MANAGE -> profileFocusId(0)
            Mode.EDIT -> "profile-name"
        })
    }

    Box(modifier.fillMaxSize().background(Tokens.BgCanvas)) {
        when (mode) {
            Mode.PICK -> Column(Modifier.fillMaxSize().padding(Spacing.SafeHorizontal), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Who's watching?", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
                Row(Modifier.padding(vertical = Spacing.S4), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                    model.profiles.forEachIndexed { i, p ->
                        OsFocusable(id = profileFocusId(i), memory = memory, onSelect = { model.onChoose(p) }) { _ -> Tile(p) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                    ActionButton(MANAGE_ID, "Manage profiles", memory) { model.guardManage { mode = Mode.MANAGE } }
                    model.onClose?.let { close -> ActionButton(CLOSE_ID, "Close", memory) { close() } }
                }
            }
            Mode.MANAGE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                Text("Profiles", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
                model.profiles.forEachIndexed { i, p ->
                    OsFocusable(id = profileFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { editing = p; mode = Mode.EDIT }) { _ ->
                        Row(Modifier.fillMaxWidth().padding(Spacing.S2), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(24.dp).height(24.dp).background(PROFILE_COLORS[p.colorIndex.mod(PROFILE_COLORS.size)]))
                            Text(p.name, style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(start = Spacing.S2).width(500.dp), maxLines = 1)
                            Text(if (p.isKids) "Kids" else "Adult", style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.weight(1f))
                            Text("Edit", style = Tokens.Body, color = Tokens.AccentPrimary)
                        }
                    }
                }
                // A full-width row like the profiles above it: a narrow button at the left lost Down from a full-width row to
                // Done, which the focus search judged nearer to the row's centre.
                OsFocusable(id = ADD_ID, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth().focusProperties { down = memory.requesterFor("profile-pin") }, onSelect = { editing = null; mode = Mode.EDIT }) { _ ->
                    Text("+  Add a profile", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(Spacing.S2))
                }
                Row(Modifier.padding(top = Spacing.S2), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                    ActionButton("profile-pin", if (model.pinIsSet) "Change the household PIN" else "Set a household PIN", memory) { model.onChangePin() }
                    if (model.pinIsSet && model.profiles.none { it.isKids }) ActionButton("profile-pin-remove", "Remove the PIN", memory) { model.onRemovePin() }
                    ActionButton(CLOSE_ID, "Done", memory) { back() }
                }
                Text("The PIN protects Settings, profile changes and adult categories. Kids profiles need it to leave.", style = Tokens.Body, color = Tokens.TextSecondary)
            }
            Mode.EDIT -> EditForm(model, editing, memory, onCancel = { mode = Mode.MANAGE }, onSaved = { d -> model.onSave(d); mode = Mode.MANAGE },
                onDelete = { confirmDelete = it }, onKidsCategories = { model.onOpenKidsCategories(it); kidsPicker = it })
        }
        kidsPicker?.let { p ->
            ChoicePicker(
                "Categories ${p.name} can open", model.kidsChoices, model.kidsAllowed, multi = true,
                note = "Kids see only the categories ticked here, and within them only titles with a known age label at or below the profile's limit.",
                memory = memory, returnFocusId = "profile-kidscats", onToggle = { id -> model.onToggleKidsCategory(p, id) }, onClose = { kidsPicker = null },
            )
        }
        confirmDelete?.let { p ->
            ChoicePicker(
                "Delete ${p.name} and everything it saved?", listOf(Choice("yes", "Yes, delete"), Choice("no", "Cancel")), emptySet(), multi = false, note = "Favorites, history and settings for this profile are removed.",
                memory = memory, returnFocusId = null, onToggle = { id -> if (id == "yes") { model.onDelete(p); mode = Mode.MANAGE } }, onClose = { confirmDelete = null },
            )
        }
    }
}

@Composable
private fun Tile(p: ProfileInfo) {
    Column(Modifier.width(240.dp).padding(Spacing.S2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Box(Modifier.width(160.dp).height(160.dp).background(PROFILE_COLORS[p.colorIndex.mod(PROFILE_COLORS.size)]), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), style = Tokens.TitleLarge, color = Color.White)
        }
        Text(p.name, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
        if (p.isKids) Text("Kids", style = Tokens.Body, color = Tokens.AccentPrimary)
    }
}

@Composable
private fun ActionButton(id: String, label: String, memory: FocusMemory, onSelect: () -> Unit) {
    OsFocusable(id = id, memory = memory, variant = FocusVariant.BUTTON, onSelect = onSelect) { _ ->
        Text(label, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
    }
}

@Composable
private fun EditForm(
    model: ProfilesModel, existing: ProfileInfo?, memory: FocusMemory, onCancel: () -> Unit, onSaved: (ProfileDraft) -> Unit,
    onDelete: (ProfileInfo) -> Unit, onKidsCategories: (ProfileInfo) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kids by remember { mutableStateOf(existing?.isKids ?: false) }
    var age by remember { mutableStateOf(existing?.maxAgeLevel ?: ParentalPolicy.DEFAULT_KIDS_MAX_AGE) }
    var adult by remember { mutableStateOf(existing?.showAdultCategories ?: false) }
    var color by remember { mutableStateOf(existing?.colorIndex ?: (model.profiles.size % PROFILE_COLORS.size)) }
    var error by remember { mutableStateOf<String?>(null) }
    var agePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(if (existing == null) "New profile" else "Edit ${existing.name}", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
        OsTextField(id = "profile-name", memory = memory, value = name, onValueChange = { name = it; error = null }, label = "Name", error = error, modifier = Modifier.width(700.dp))
        FormRow("profile-type", "Type", if (kids) "Kids: limited titles, no Live TV" else "Adult", memory) { kids = !kids; if (kids) adult = false }
        FormRow("profile-color", "Colour", "Select to change", memory, swatch = PROFILE_COLORS[color.mod(PROFILE_COLORS.size)]) { color = (color + 1) % PROFILE_COLORS.size }
        if (kids) {
            FormRow("profile-age", "Highest age label", AGE_CHOICES.firstOrNull { it.id == "$age" }?.label ?: "Up to age $age", memory) { agePicker = true }
            if (existing != null) FormRow("profile-kidscats", "Categories it can open", "Select to choose", memory) { onKidsCategories(existing) }
            else Text("Suggested kids categories are added automatically; change them here after saving.", style = Tokens.Body, color = Tokens.TextSecondary)
        } else {
            FormRow("profile-adult", "Adult categories", if (adult) "Shown" else "Hidden", memory) { adult = !adult }
        }
        Row(Modifier.padding(top = Spacing.S2), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            ActionButton("profile-save", "Save", memory) {
                if (name.isBlank()) error = "Give the profile a name" else onSaved(ProfileDraft(existing?.id ?: 0, name.trim(), kids, age, adult, color))
            }
            ActionButton("profile-cancel", "Cancel", memory) { onCancel() }
            if (existing != null && model.profiles.size > 1) ActionButton("profile-delete", "Delete", memory) { onDelete(existing) }
        }
    }
    if (agePicker) ChoicePicker("Highest age label", AGE_CHOICES, setOf("$age"), multi = false, note = "Titles with no age label are always hidden from Kids.", memory = memory, returnFocusId = "profile-age", onToggle = { age = it.toInt() }, onClose = { agePicker = false })
}

@Composable
private fun FormRow(id: String, title: String, value: String, memory: FocusMemory, swatch: Color? = null, onSelect: () -> Unit) {
    OsFocusable(id = id, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = onSelect) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.width(420.dp), maxLines = 1)
            swatch?.let { Box(Modifier.width(24.dp).height(24.dp).background(it)); Box(Modifier.width(Spacing.S1)) }
            Text(value, style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
        }
    }
}
