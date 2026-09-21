package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.components.GameCard
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary

/**
 * Liste des jeux Terminé/Abandonné d'une année, ouverte depuis `RecapScreen` (voir
 * `RecapGamesViewModel`) — tape sur une carte pousse la fiche Détail, comme sur la Bibliothèque.
 */
@Composable
fun RecapGamesScreen(
    onBackClick: () -> Unit,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapGamesViewModel = hiltViewModel(),
) {
    val games by viewModel.games.collectAsStateWithLifecycle()
    RecapGamesContent(
        year = viewModel.year,
        filter = viewModel.filter,
        games = games,
        onBackClick = onBackClick,
        onGameClick = onGameClick,
        modifier = modifier,
    )
}

@Composable
private fun RecapGamesContent(
    year: Int,
    filter: BacklogFilter,
    games: List<Game>,
    onBackClick: () -> Unit,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = if (filter == BacklogFilter.ABANDONNE) {
        R.string.recap_games_title_abandonne
    } else {
        R.string.recap_games_title_termine
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        StatsBackHeader(label = stringResource(R.string.recap_games_back), onBackClick = onBackClick)
        Column(
            modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
        ) {
            Text(
                text = stringResource(titleRes, year),
                style = CartoucheTextStyles.screenTitle,
                color = TextPrimary,
            )
        }
        if (games.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.recap_games_empty),
                    style = CartoucheTextStyles.emptyMessage,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = games, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onGameClick(game.id) })
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapGamesContentPreview() {
    val games = listOf(
        Game(id = "1", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE),
        Game(id = "2", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE),
    )
    CartoucheTheme {
        RecapGamesContent(
            year = 2026,
            filter = BacklogFilter.TERMINE,
            games = games,
            onBackClick = {},
            onGameClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapGamesContentEmptyPreview() {
    CartoucheTheme {
        RecapGamesContent(
            year = 2026,
            filter = BacklogFilter.ABANDONNE,
            games = emptyList(),
            onBackClick = {},
            onGameClick = {},
        )
    }
}
