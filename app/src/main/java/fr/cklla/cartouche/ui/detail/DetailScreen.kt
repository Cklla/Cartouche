package fr.cklla.cartouche.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.components.GameCoverPlaceholder
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleMuted
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.ErrorCoral
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.SurfaceCardPressed
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary
import fr.cklla.cartouche.ui.theme.TextTertiary
import fr.cklla.cartouche.ui.theme.labelRes
import fr.cklla.cartouche.ui.theme.palette
import java.util.Locale

@Composable
fun DetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Le jeu devient `null` après chargement soit parce que l'id est invalide,
    // soit après "Retirer du backlog" (voir DetailViewModel) : dans les deux cas
    // on revient simplement à l'écran précédent plutôt que d'afficher un écran cassé.
    LaunchedEffect(uiState.isLoading, uiState.game) {
        if (!uiState.isLoading && uiState.game == null) onBackClick()
    }

    val game = uiState.game ?: return

    DetailContent(
        game = game,
        isInBacklog = uiState.isInBacklog,
        onBackClick = onBackClick,
        onStatusSelected = viewModel::onStatusSelected,
        onRatingSelected = viewModel::onRatingSelected,
        onHoursIncrement = viewModel::onHoursIncrement,
        onHoursDecrement = viewModel::onHoursDecrement,
        onHoursSet = viewModel::onHoursSet,
        onNotesChanged = viewModel::onNotesChanged,
        onRemoveGame = viewModel::onRemoveGame,
        onAddGame = viewModel::onAddGame,
        modifier = modifier,
    )
}

@Composable
private fun DetailContent(
    game: Game,
    isInBacklog: Boolean,
    onBackClick: () -> Unit,
    onStatusSelected: (GameStatus) -> Unit,
    onRatingSelected: (Int) -> Unit,
    onHoursIncrement: () -> Unit,
    onHoursDecrement: () -> Unit,
    onHoursSet: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRemoveGame: () -> Unit,
    onAddGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        BackHeader(onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            GameCoverPlaceholder(
                title = game.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                coverUrl = game.coverUrl,
                letterStyle = CartoucheTextStyles.coverLetterLarge,
                letterAlpha = 0.14f,
                showLabel = true,
            )
            TitleSection(game = game)
            // Statut, note perso, temps de jeu perso et notes libres n'ont de sens que pour un
            // jeu réellement possédé : masqués tant que la fiche n'est qu'un aperçu ouvert depuis
            // la Recherche (voir `DetailUiState.isInBacklog`).
            if (isInBacklog) {
                StatusSection(selected = game.status, onStatusSelected = onStatusSelected)
                RatingSection(rating = game.rating, onRatingSelected = onRatingSelected)
            }
            EstimatedPlaytimeSection(
                hastily = game.estimatedPlaytimeHastilyHours,
                normally = game.estimatedPlaytimeNormallyHours,
                completely = game.estimatedPlaytimeCompletelyHours,
            )
            if (isInBacklog) {
                HoursSection(
                    hours = game.userPlaytimeHours,
                    onIncrement = onHoursIncrement,
                    onDecrement = onHoursDecrement,
                    onHoursSet = onHoursSet,
                )
                NotesSection(notes = game.notes, onNotesChanged = onNotesChanged)
                RemoveLink(onClick = { showRemoveConfirm = true })
            } else {
                AddToBacklogButton(onClick = onAddGame)
            }
        }
    }

    if (showRemoveConfirm) {
        RemoveConfirmDialog(
            onConfirm = {
                showRemoveConfirm = false
                onRemoveGame()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun BackHeader(onBackClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = TextPrimary,
            )
            Text(
                text = stringResource(R.string.detail_back),
                style = CartoucheTextStyles.backLabel,
                color = TextTertiary,
            )
        }
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.6f), thickness = 0.5.dp)
    }
}

@Composable
private fun TitleSection(game: Game) {
    Column {
        Text(text = game.title, style = CartoucheTextStyles.detailTitle, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.game_card_platform_genre, game.platform, game.genre),
            style = CartoucheTextStyles.detailSubtitle,
            color = TextTertiary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text.uppercase(Locale.FRENCH), style = CartoucheTextStyles.sectionLabel, color = TextMuted)
}

@Composable
private fun StatusSection(selected: GameStatus, onStatusSelected: (GameStatus) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.detail_status_label))
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            GameStatus.entries.forEach { status ->
                StatusPill(status = status, selected = status == selected, onClick = { onStatusSelected(status) })
            }
        }
    }
}

@Composable
private fun StatusPill(status: GameStatus, selected: Boolean, onClick: () -> Unit) {
    val palette = status.palette()
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) {
                    Modifier.background(palette.color)
                } else {
                    Modifier
                        .background(palette.badgeBackground)
                        .border(BorderStroke(0.5.dp, palette.badgeBorder), RoundedCornerShape(20.dp))
                },
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(status.labelRes()),
            style = CartoucheTextStyles.statusPillLabel,
            color = if (selected) BackgroundDark else palette.color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RatingSection(rating: Int?, onRatingSelected: (Int) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.detail_rating_label))
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            for (star in 1..5) {
                val filled = rating != null && star <= rating
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = { onRatingSelected(star) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = stringResource(R.string.detail_rating_star_content_description, star),
                        tint = if (filled) AccentPurple else AccentPurpleMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/**
 * Temps de jeu estimé par IGDB (rapide/normal/complet), en lecture seule — distinct de
 * [HoursSection] (temps de jeu personnel, éditable) : jamais dans le même bloc UI, pour ne pas
 * laisser croire que ces valeurs sont modifiables ou qu'elles viennent du joueur.
 *
 * Seules les valeurs effectivement renseignées par IGDB sont affichées (certains jeux n'ont
 * qu'une partie des trois, ex. seul "normally" pour *Trails in the Sky First Chapter*) ; si
 * aucune des trois n'est disponible, un unique message "Non disponible" est affiché à la place.
 */
@Composable
private fun EstimatedPlaytimeSection(hastily: Int?, normally: Int?, completely: Int?) {
    val entries = listOfNotNull(
        hastily?.let { R.string.detail_estimated_playtime_hastily_label to it },
        normally?.let { R.string.detail_estimated_playtime_normally_label to it },
        completely?.let { R.string.detail_estimated_playtime_completely_label to it },
    )

    Column {
        SectionLabel(stringResource(R.string.detail_estimated_playtime_label))
        Spacer(modifier = Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_estimated_playtime_unavailable),
                style = CartoucheTextStyles.hoursValue,
                color = TextMuted,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                entries.forEach { (labelRes, hours) -> EstimatedPlaytimeEntry(labelRes = labelRes, hours = hours) }
            }
        }
    }
}

@Composable
private fun EstimatedPlaytimeEntry(labelRes: Int, hours: Int) {
    Column {
        Text(text = stringResource(labelRes), style = CartoucheTextStyles.cardSubtitle, color = TextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.detail_estimated_playtime_value, hours),
            style = CartoucheTextStyles.estimatedPlaytimeValue,
            color = TextSecondary,
        )
    }
}

@Composable
private fun HoursSection(
    hours: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onHoursSet: (Int) -> Unit,
) {
    Column {
        SectionLabel(stringResource(R.string.detail_hours_label))
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HoursStepButton(
                icon = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.detail_hours_decrement),
                enabled = hours > 0,
                onClick = onDecrement,
            )
            HoursValueField(hours = hours, onHoursSet = onHoursSet)
            HoursStepButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.detail_hours_increment),
                enabled = true,
                onClick = onIncrement,
            )
        }
    }
}

// Nombre maximal de chiffres acceptés en saisie manuelle (5 chiffres = jusqu'à 99999h, largement
// au-delà de n'importe quel jeu réel — juste une garde-fou contre une saisie absurde).
private const val MAX_HOURS_DIGITS = 5

/**
 * Affichage du temps de jeu perso, qui devient un champ de saisie numérique au tap — pensé pour
 * les jeux très longs (ex. 380h) où incrémenter un par un via [HoursStepButton] serait fastidieux.
 * Les boutons +/- restent utilisables normalement après une saisie manuelle, puisque la valeur
 * affichée reste la même source de vérité ([hours]) dans les deux cas.
 */
@Composable
private fun HoursValueField(hours: Int, onHoursSet: (Int) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    // Réinitialisée à chaque entrée en mode édition (clé `isEditing`) : pré-remplie avec la valeur
    // actuelle, texte entièrement sélectionné pour permettre de la remplacer d'une seule frappe.
    var fieldValue by remember(isEditing) {
        mutableStateOf(
            if (isEditing) {
                val text = hours.toString()
                TextFieldValue(text = text, selection = TextRange(0, text.length))
            } else {
                TextFieldValue()
            },
        )
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // `Modifier.onFocusChanged` est aussi déclenché une première fois dès l'attachement du champ,
    // avec `isFocused = false` (avant même que `LaunchedEffect` ci-dessous n'ait pu appeler
    // `requestFocus()`) — sans cette garde, ce tout premier appel valait "perte de focus" et
    // refermait le champ instantanément (à peine un flash à l'écran, jamais éditable). On ne
    // valide donc une perte de focus que si le champ avait bien été focus au moins une fois avant.
    var hasBeenFocused by remember(isEditing) { mutableStateOf(false) }
    val editContentDescription = stringResource(R.string.detail_hours_edit_content_description, hours)
    val inputContentDescription = stringResource(R.string.detail_hours_input_content_description)

    fun commit() {
        fieldValue.text.toIntOrNull()?.let(onHoursSet)
        isEditing = false
    }

    if (isEditing) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new.copy(text = new.text.filter(Char::isDigit).take(MAX_HOURS_DIGITS))
            },
            singleLine = true,
            textStyle = CartoucheTextStyles.hoursValue.copy(color = TextPrimary),
            cursorBrush = SolidColor(AccentPurple),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            // On ne valide pas directement ici : on relâche le focus proprement (masque le
            // clavier), ce qui déclenche `onFocusChanged` ci-dessous et commit *à ce moment-là*.
            // En committant directement ici, le champ disparaissait de la composition (isEditing
            // = false) alors qu'il était encore focus — Compose devait alors reporter le focus
            // ailleurs dans l'écran de son propre chef, et retombait sur la première zone
            // cliquable de l'écran (l'en-tête "Retour à la bibliothèque"), qui affichait alors
            // brièvement son halo de focus (violet) : effet de bord purement visuel, sans lien
            // avec la donnée saisie.
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 32.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hasBeenFocused = true
                    } else if (hasBeenFocused) {
                        commit()
                    }
                }
                .semantics { contentDescription = inputContentDescription },
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Text(
            text = stringResource(R.string.detail_hours_value, hours),
            style = CartoucheTextStyles.hoursValue,
            color = TextPrimary,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .clip(RoundedCornerShape(6.dp))
                .clickable { isEditing = true }
                .semantics { contentDescription = editContentDescription },
        )
    }
}

@Composable
private fun HoursStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        // Zone de clic 48dp (accessibilité) autour du bouton visuel 34dp de la maquette.
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceCardPressed)
                .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) TextPrimary else TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun NotesSection(notes: String, onNotesChanged: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) AccentPurple else BorderHairline.copy(alpha = 0.6f)
    val notesLabel = stringResource(R.string.detail_notes_label)

    Column {
        SectionLabel(notesLabel)
        Spacer(modifier = Modifier.height(10.dp))
        BasicTextField(
            value = notes,
            onValueChange = onNotesChanged,
            interactionSource = interactionSource,
            textStyle = CartoucheTextStyles.notesText.copy(color = TextSecondary),
            cursorBrush = SolidColor(AccentPurple),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .semantics { contentDescription = notesLabel },
            decorationBox = { innerTextField ->
                if (notes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_notes_placeholder),
                        style = CartoucheTextStyles.notesText,
                        color = AccentPurpleMuted,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun RemoveLink(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.detail_remove_action),
            style = CartoucheTextStyles.linkLabel.copy(textDecoration = TextDecoration.Underline),
            color = ErrorCoral,
        )
    }
}

@Composable
private fun AddToBacklogButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AccentPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_add_action),
            style = CartoucheTextStyles.statusPillLabel,
            color = TextPrimary,
        )
    }
}

@Composable
private fun RemoveConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextTertiary,
        title = { Text(stringResource(R.string.detail_remove_confirm_title)) },
        text = { Text(stringResource(R.string.detail_remove_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.detail_remove_confirm_confirm), color = ErrorCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.detail_remove_confirm_cancel), color = TextTertiary)
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun DetailContentPreview() {
    val game = Game(
        id = "1",
        title = "The Legend of Zelda: Tears of the Kingdom",
        platform = "Switch",
        genre = "Aventure",
        status = GameStatus.EN_COURS,
        userPlaytimeHours = 34,
        estimatedPlaytimeHastilyHours = 38,
        estimatedPlaytimeNormallyHours = 48,
        estimatedPlaytimeCompletelyHours = 96,
        rating = 4,
        notes = "Exploration incroyable, à reprendre le week-end.",
    )
    CartoucheTheme {
        DetailContent(
            game = game,
            isInBacklog = true,
            onBackClick = {},
            onStatusSelected = {},
            onRatingSelected = {},
            onHoursIncrement = {},
            onHoursDecrement = {},
            onHoursSet = {},
            onNotesChanged = {},
            onRemoveGame = {},
            onAddGame = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun DetailContentApercuPreview() {
    val game = Game(
        title = "Hollow Knight",
        platform = "PC",
        genre = "Metroidvania",
        status = GameStatus.A_FAIRE,
        estimatedPlaytimeNormallyHours = 27,
    )
    CartoucheTheme {
        DetailContent(
            game = game,
            isInBacklog = false,
            onBackClick = {},
            onStatusSelected = {},
            onRatingSelected = {},
            onHoursIncrement = {},
            onHoursDecrement = {},
            onHoursSet = {},
            onNotesChanged = {},
            onRemoveGame = {},
            onAddGame = {},
        )
    }
}
