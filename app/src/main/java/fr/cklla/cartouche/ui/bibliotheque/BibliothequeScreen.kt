package fr.cklla.cartouche.ui.bibliotheque

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.components.GameCoverPlaceholder
import fr.cklla.cartouche.ui.components.StatusBadge
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.AccentPurpleMuted
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary
import fr.cklla.cartouche.ui.theme.TextTertiary

@Composable
fun BibliothequeScreen(
    modifier: Modifier = Modifier,
    onGameClick: (Long) -> Unit = {},
    viewModel: BibliothequeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BibliothequeContent(
        uiState = uiState,
        onFilterSelected = viewModel::onFilterSelected,
        onGameClick = onGameClick,
        modifier = modifier,
    )
}

@Composable
private fun BibliothequeContent(
    uiState: BibliothequeUiState,
    onFilterSelected: (BacklogFilter) -> Unit,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header()
        FilterChipsRow(
            selectedFilter = uiState.selectedFilter,
            counts = uiState.filterCounts,
            onFilterSelected = onFilterSelected,
        )
        if (uiState.visibleGames.isEmpty()) {
            EmptyState(filter = uiState.selectedFilter, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.visibleGames, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onGameClick(game.id) })
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.bibliotheque_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.bibliotheque_title),
            style = CartoucheTextStyles.screenTitle,
            color = TextPrimary,
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: BacklogFilter,
    counts: Map<BacklogFilter, Int>,
    onFilterSelected: (BacklogFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = BacklogFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                filter = filter,
                count = counts[filter] ?: 0,
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterChip(filter: BacklogFilter, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(AccentPurple)
                else Modifier.border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(20.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.filter_chip_label, stringResource(filter.labelRes), count),
            style = CartoucheTextStyles.chipLabel,
            color = if (selected) TextPrimary else TextTertiary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameCoverPlaceholder(title = game.title, width = 56.dp, height = 76.dp, coverUrl = game.coverUrl)
        Column {
            Text(
                text = game.title,
                style = CartoucheTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.game_card_platform_genre, game.platform, game.genre),
                style = CartoucheTextStyles.cardSubtitle,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusBadge(status = game.status)
        }
    }
}

@Composable
private fun EmptyState(filter: BacklogFilter, modifier: Modifier = Modifier) {
    val messageRes = when (filter) {
        BacklogFilter.TOUS -> R.string.empty_message_tous
        BacklogFilter.A_FAIRE -> R.string.empty_message_a_faire
        BacklogFilter.EN_COURS -> R.string.empty_message_en_cours
        BacklogFilter.TERMINE -> R.string.empty_message_termine
        BacklogFilter.ABANDONNE -> R.string.empty_message_abandonne
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, AccentPurpleMuted), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .rotate(45f)
                    .border(BorderStroke(1.dp, AccentPurpleMuted)),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.empty_title), style = CartoucheTextStyles.emptyTitle, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(messageRes),
            style = CartoucheTextStyles.emptyMessage,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun BibliothequeContentPreview() {
    val games = listOf(
        Game(id = 1, title = "The Legend of Zelda: Tears of the Kingdom", platform = "Switch", genre = "Aventure", status = GameStatus.EN_COURS),
        Game(id = 2, title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE),
        Game(id = 3, title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE),
    )
    CartoucheTheme {
        BibliothequeContent(
            uiState = BibliothequeUiState(
                isLoading = false,
                visibleGames = games,
                selectedFilter = BacklogFilter.TOUS,
                filterCounts = countByFilter(games),
            ),
            onFilterSelected = {},
            onGameClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun BibliothequeEmptyPreview() {
    CartoucheTheme {
        BibliothequeContent(
            uiState = BibliothequeUiState(isLoading = false, selectedFilter = BacklogFilter.ABANDONNE),
            onFilterSelected = {},
            onGameClick = {},
        )
    }
}
