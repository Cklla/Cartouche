package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.SuccessGreen
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextTertiary
import fr.cklla.cartouche.ui.theme.palette

/**
 * Écran ouvert au clic sur la carte "Récap" de Stats. Reprend les chiffres de [StatsData] déjà
 * calculés pour l'année ciblée (voir `RecapViewModel`, `computeStats(games, selectedYear = year)`)
 * dans une présentation plus posée que la grille compacte de `StatsScreen` — mais sans introduire
 * de métrique qui n'existe pas déjà dans `StatsCalculations`.
 */
@Composable
fun RecapScreen(
    onBackClick: () -> Unit,
    onGamesClick: (Int, BacklogFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecapContent(
        year = viewModel.year,
        stats = uiState,
        onBackClick = onBackClick,
        onGamesClick = { filter -> onGamesClick(viewModel.year, filter) },
        modifier = modifier,
    )
}

@Composable
private fun RecapContent(
    year: Int,
    stats: StatsData,
    onBackClick: () -> Unit,
    onGamesClick: (BacklogFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        StatsBackHeader(label = stringResource(R.string.recap_back), onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            RecapKicker(year = year)
            RecapHeroCard(stats = stats, onGamesClick = onGamesClick)
            PlatformBreakdownSection(
                titleRes = R.string.stats_completed_by_platform_label,
                counts = stats.completedByPlatform,
                dotColor = GameStatus.TERMINE.palette().color,
            )
        }
    }
}

/**
 * En-tête "retour" partagé par `RecapScreen` et `RecapGamesScreen` (même style que
 * `DetailScreen.BackHeader`, dupliqué ici plutôt que remonté en composant global pour ne pas
 * toucher un écran déjà en production pour ce correctif).
 */
@Composable
internal fun StatsBackHeader(label: String, onBackClick: () -> Unit) {
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
                text = label,
                style = CartoucheTextStyles.backLabel,
                color = TextTertiary,
            )
        }
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.6f), thickness = 0.5.dp)
    }
}

@Composable
private fun RecapKicker(year: Int) {
    Column {
        Text(
            text = stringResource(R.string.recap_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.recap_title, year),
            style = CartoucheTextStyles.screenTitle,
            color = TextPrimary,
        )
    }
}

/**
 * Nombre de jeux terminés mis en avant (gros chiffre central), heures de jeu et jeux abandonnés
 * en secondaire — les trois valeurs viennent telles quelles de [stats] (année déjà ciblée par
 * `RecapViewModel`), aucun nouveau calcul ici. Le nombre de jeux terminés et celui de jeux
 * abandonnés sont cliquables (pas les heures, qui ne correspondent à aucune liste de jeux à part
 * entière) et ouvrent la liste des jeux concernés via [onGamesClick] — désactivés à 0, inutile de
 * naviguer vers une liste qu'on sait vide.
 */
@Composable
private fun RecapHeroCard(stats: StatsData, onGamesClick: (BacklogFilter) -> Unit) {
    val abandonedCount = stats.countsByStatus[GameStatus.ABANDONNE] ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = stats.completedCount > 0) { onGamesClick(BacklogFilter.TERMINE) }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stats.completedCount.toString(), style = CartoucheTextStyles.donutPercent, color = SuccessGreen)
            Text(
                text = stringResource(R.string.recap_completed_label),
                style = CartoucheTextStyles.cardSubtitle,
                color = TextTertiary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = BorderHairline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RecapSecondaryStat(
                value = stats.totalHoursPlayed.toString(),
                label = stringResource(R.string.recap_hours_label),
                valueColor = AccentPurple,
            )
            RecapSecondaryStat(
                value = abandonedCount.toString(),
                label = stringResource(R.string.recap_abandoned_label),
                valueColor = GameStatus.ABANDONNE.palette().color,
                onClick = if (abandonedCount > 0) ({ onGamesClick(BacklogFilter.ABANDONNE) }) else null,
            )
        }
    }
}

@Composable
private fun RecapSecondaryStat(value: String, label: String, valueColor: Color, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = CartoucheTextStyles.statValueMedium, color = valueColor)
        Text(text = label, style = CartoucheTextStyles.cardSubtitle, color = TextTertiary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapContentPreview() {
    val games = listOf(
        GameStatus.TERMINE to setOf("PC"),
        GameStatus.TERMINE to setOf("PS5"),
        GameStatus.TERMINE to setOf("Switch"),
        GameStatus.ABANDONNE to setOf("PC"),
    ).mapIndexed { index, (status, playedPlatforms) ->
        fr.cklla.cartouche.domain.model.Game(
            id = index.toString(),
            title = "Jeu $index",
            platform = "PC",
            genre = "Aventure",
            status = status,
            userPlaytimeHours = index * 12,
            playedPlatforms = playedPlatforms,
        )
    }
    CartoucheTheme {
        RecapContent(
            year = 2026,
            stats = computeStats(games, selectedYear = 2026),
            onBackClick = {},
            onGamesClick = {},
        )
    }
}
