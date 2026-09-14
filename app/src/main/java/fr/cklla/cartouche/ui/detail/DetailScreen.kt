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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        onBackClick = onBackClick,
        onStatusSelected = viewModel::onStatusSelected,
        onRatingSelected = viewModel::onRatingSelected,
        onHoursIncrement = viewModel::onHoursIncrement,
        onHoursDecrement = viewModel::onHoursDecrement,
        onNotesChanged = viewModel::onNotesChanged,
        onRemoveGame = viewModel::onRemoveGame,
        modifier = modifier,
    )
}

@Composable
private fun DetailContent(
    game: Game,
    onBackClick: () -> Unit,
    onStatusSelected: (GameStatus) -> Unit,
    onRatingSelected: (Int) -> Unit,
    onHoursIncrement: () -> Unit,
    onHoursDecrement: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onRemoveGame: () -> Unit,
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
            StatusSection(selected = game.status, onStatusSelected = onStatusSelected)
            RatingSection(rating = game.rating, onRatingSelected = onRatingSelected)
            HoursSection(hours = game.hoursPlayed, onIncrement = onHoursIncrement, onDecrement = onHoursDecrement)
            NotesSection(notes = game.notes, onNotesChanged = onNotesChanged)
            RemoveLink(onClick = { showRemoveConfirm = true })
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

@Composable
private fun HoursSection(hours: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
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
            Text(
                text = stringResource(R.string.detail_hours_value, hours),
                style = CartoucheTextStyles.hoursValue,
                color = TextPrimary,
            )
            HoursStepButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.detail_hours_increment),
                enabled = true,
                onClick = onIncrement,
            )
        }
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
        id = 1,
        title = "The Legend of Zelda: Tears of the Kingdom",
        platform = "Switch",
        genre = "Aventure",
        status = GameStatus.EN_COURS,
        hoursPlayed = 34,
        rating = 4,
        notes = "Exploration incroyable, à reprendre le week-end.",
    )
    CartoucheTheme {
        DetailContent(
            game = game,
            onBackClick = {},
            onStatusSelected = {},
            onRatingSelected = {},
            onHoursIncrement = {},
            onHoursDecrement = {},
            onNotesChanged = {},
            onRemoveGame = {},
        )
    }
}
