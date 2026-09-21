package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.components.YearChipsRow
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.SuccessGreen
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary
import fr.cklla.cartouche.ui.theme.TextTertiary
import fr.cklla.cartouche.ui.theme.labelRes
import fr.cklla.cartouche.ui.theme.palette
import java.util.Locale

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onRecapClick: (Int) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val recapStats by viewModel.recapStats.collectAsStateWithLifecycle()
    StatsContent(
        stats = uiState,
        signedInAs = currentUser?.displayName,
        onSignOutClick = viewModel::onSignOutClicked,
        onYearSelected = viewModel::onYearSelected,
        recapYear = viewModel.recapYear,
        recapStats = recapStats,
        onRecapClick = onRecapClick,
        modifier = modifier,
    )
}

@Composable
private fun StatsContent(
    stats: StatsData,
    signedInAs: String?,
    onSignOutClick: () -> Unit,
    onYearSelected: (Int?) -> Unit,
    recapYear: Int?,
    recapStats: StatsData,
    onRecapClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header(signedInAs = signedInAs, onSignOutClick = onSignOutClick)
        if (stats.availableYears.isNotEmpty()) {
            YearChipsRow(
                years = stats.availableYears,
                selectedYear = stats.selectedYear,
                onYearSelected = onYearSelected,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Carte "Récap" : uniquement dans la fenêtre calculée par `recapTargetYear` (fin
            // décembre / tout janvier, voir StatsViewModel.recapYear), quelle que soit l'année
            // sélectionnée par ailleurs sur cet écran via les chips (les deux sont indépendants).
            if (recapYear != null) {
                RecapCard(year = recapYear, stats = recapStats, onClick = { onRecapClick(recapYear) })
            }
            StatCardsGrid(stats = stats)
            DonutSection(stats = stats)
            PlatformBreakdownSection(
                titleRes = R.string.stats_completed_by_platform_label,
                counts = stats.completedByPlatform,
                dotColor = GameStatus.TERMINE.palette().color,
            )
            PlatformBreakdownSection(
                titleRes = R.string.stats_in_progress_by_platform_label,
                counts = stats.inProgressByPlatform,
                dotColor = GameStatus.EN_COURS.palette().color,
            )
        }
    }
}

@Composable
private fun Header(signedInAs: String?, onSignOutClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.stats_title),
            style = CartoucheTextStyles.screenTitle,
            color = TextPrimary,
        )
        if (signedInAs != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.stats_signed_in_as, signedInAs),
                    style = CartoucheTextStyles.legendLabel,
                    color = TextTertiary,
                )
                Text(
                    text = stringResource(R.string.stats_sign_out),
                    style = CartoucheTextStyles.legendLabel,
                    color = AccentPurpleLight,
                    modifier = Modifier.clickable(onClick = onSignOutClick),
                )
            }
        }
    }
}

/**
 * Carte "Récap" de l'année ciblée (voir `StatsViewModel.recapYear`/[recapTargetYear]) — mise en
 * avant par une bordure et un fond teintés [AccentPurple] pour se distinguer des cartes-chiffres
 * neutres de [StatCardsGrid] en dessous, puisqu'elle est cliquable et n'apparaît que quelques
 * semaines par an. Les deux chiffres affichés (jeux terminés, heures de jeu) viennent de [stats],
 * déjà calculées en direct sur l'année ciblée par `StatsViewModel` — aucune métrique inédite.
 */
@Composable
private fun RecapCard(year: Int, stats: StatsData, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentPurple.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_recap_card_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.stats_recap_card_title, year),
                    style = CartoucheTextStyles.statValueMedium,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.stats_recap_card_subtitle,
                        stats.completedCount,
                        stats.totalHoursPlayed,
                    ),
                    style = CartoucheTextStyles.cardSubtitle,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AccentPurpleLight,
            )
        }
    }
}

@Composable
private fun StatCardsGrid(stats: StatsData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stats.selectedYear == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    value = stats.completedCount.toString(),
                    label = stringResource(R.string.stats_completed_label),
                    valueColor = SuccessGreen,
                    modifier = Modifier.weight(1f),
                )
                // "Taille du backlog" n'a plus de sens une fois filtré sur une année : un jeu
                // À faire/En cours n'a pas d'année de complétion, il n'y a donc rien à compter.
                StatCard(
                    value = stats.backlogSize.toString(),
                    label = stringResource(R.string.stats_backlog_size_label),
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            StatCard(
                value = stats.completedCount.toString(),
                label = stringResource(R.string.stats_completed_label),
                valueColor = SuccessGreen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HoursCard(hours = stats.totalHoursPlayed)
    }
}

@Composable
private fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Text(text = value, style = CartoucheTextStyles.statValueLarge, color = valueColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = CartoucheTextStyles.cardSubtitle, color = TextTertiary)
    }
}

@Composable
private fun HoursCard(hours: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = hours.toString(),
            style = CartoucheTextStyles.statValueMedium,
            color = AccentPurple,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(R.string.stats_hours_label),
            style = CartoucheTextStyles.cardSubtitle,
            color = TextTertiary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun DonutSection(stats: StatsData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DonutChart(stats = stats)
        LegendList(stats = stats)
    }
}

/** Les deux seuls statuts datés, dans l'ordre d'affichage de l'anneau et de la légende en vue
 * "année sélectionnée" — [GameStatus.entries] complet n'a de sens qu'en vue "toutes années". */
private val YEAR_FILTERED_STATUSES = listOf(GameStatus.TERMINE, GameStatus.ABANDONNE)

/**
 * Anneau de progression, équivalent natif du `conic-gradient` CSS du prototype.
 *
 * Vue "toutes années" : un segment par statut (dans l'ordre de [GameStatus.entries]),
 * proportionnel à son nombre de jeux, dessiné en partant du haut (`startAngle = -90f`) dans le
 * sens horaire — même convention que le prototype.
 *
 * Vue "année sélectionnée" : même principe mais restreint aux deux statuts datés (Terminé/
 * Abandonné, voir [YEAR_FILTERED_STATUSES]) — À faire/En cours n'ont pas de date de transition,
 * donc pas de sens une fois filtré sur une seule année.
 */
@Composable
private fun DonutChart(stats: StatsData) {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 23.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val arcSize = Size(diameter, diameter)

            val statuses = if (stats.selectedYear != null) YEAR_FILTERED_STATUSES else GameStatus.entries
            val total = if (stats.selectedYear != null) {
                statuses.sumOf { stats.countsByStatus[it] ?: 0 }
            } else {
                stats.backlogSize
            }

            if (total == 0) {
                drawArc(
                    color = BorderHairline,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx),
                )
            } else {
                var startAngle = -90f
                statuses.forEach { status ->
                    val count = stats.countsByStatus[status] ?: 0
                    val sweep = 360f * count / total
                    if (sweep > 0f) {
                        drawArc(
                            color = status.palette().color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx),
                        )
                        startAngle += sweep
                    }
                }
            }
        }
        if (stats.selectedYear == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.stats_completion_percent, stats.completionPercent),
                    style = CartoucheTextStyles.donutPercent,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.stats_completion_label).uppercase(Locale.FRENCH),
                    style = CartoucheTextStyles.donutLabel,
                    color = TextMuted,
                )
            }
        } else {
            val total = YEAR_FILTERED_STATUSES.sumOf { stats.countsByStatus[it] ?: 0 }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toString(),
                    style = CartoucheTextStyles.donutPercent,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.stats_year_total_label).uppercase(Locale.FRENCH),
                    style = CartoucheTextStyles.donutLabel,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun LegendList(stats: StatsData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val statuses = if (stats.selectedYear == null) GameStatus.entries else YEAR_FILTERED_STATUSES
        statuses.forEach { status ->
            LegendRow(status = status, count = stats.countsByStatus[status] ?: 0)
        }
    }
}

@Composable
private fun LegendRow(status: GameStatus, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(status.palette().color),
        )
        Text(
            text = stringResource(status.labelRes()),
            style = CartoucheTextStyles.legendLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = CartoucheTextStyles.legendCount, color = TextPrimary)
    }
}

/**
 * Répartition par plateforme (voir `Game.playedPlatforms`), affichée deux fois sur cet écran :
 * une fois pour les jeux Terminé (respecte le filtre par année, voir `StatsData.completedByPlatform`)
 * et une fois pour les jeux En cours (toujours toutes années, voir `StatsData.inProgressByPlatform`).
 * Masquée entièrement quand `counts` est vide — aucune plateforme à zéro n'y figure jamais (voir
 * `platformCounts`, qui les exclut à la source), inutile d'afficher un titre de section sans lignes.
 *
 * `internal` plutôt que `private` : réutilisée telle quelle par `RecapScreen`.
 */
@Composable
internal fun PlatformBreakdownSection(titleRes: Int, counts: Map<String, Int>, dotColor: Color) {
    if (counts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(titleRes).uppercase(Locale.FRENCH),
            style = CartoucheTextStyles.sectionLabel,
            color = TextMuted,
        )
        counts.forEach { (platform, count) -> PlatformBreakdownRow(platform = platform, count = count, dotColor = dotColor) }
    }
}

@Composable
private fun PlatformBreakdownRow(platform: String, count: Int, dotColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = platform,
            style = CartoucheTextStyles.legendLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = CartoucheTextStyles.legendCount, color = TextPrimary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentPreview() {
    val games = listOf(
        GameStatus.A_FAIRE to setOf("PC"),
        GameStatus.A_FAIRE to setOf("PC"),
        GameStatus.A_FAIRE to setOf("PC"),
        GameStatus.EN_COURS to setOf("PC"),
        GameStatus.EN_COURS to setOf("Switch"),
        GameStatus.TERMINE to setOf("PC"),
        GameStatus.TERMINE to setOf("PS5"),
        GameStatus.TERMINE to setOf("Switch"),
        GameStatus.TERMINE to setOf("PC", "PS5"),
    ).mapIndexed { index, (status, playedPlatforms) ->
        fr.cklla.cartouche.domain.model.Game(
            id = index.toString(),
            title = "Jeu $index",
            platform = "PC",
            genre = "Aventure",
            status = status,
            userPlaytimeHours = index * 5,
            playedPlatforms = playedPlatforms,
        )
    }
    CartoucheTheme {
        StatsContent(
            stats = computeStats(games),
            signedInAs = "Joueur Test",
            onSignOutClick = {},
            onYearSelected = {},
            recapYear = 2026,
            recapStats = computeStats(games, selectedYear = 2026),
            onRecapClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentEmptyPreview() {
    CartoucheTheme {
        StatsContent(
            stats = StatsData(),
            signedInAs = null,
            onSignOutClick = {},
            onYearSelected = {},
            recapYear = null,
            recapStats = StatsData(),
            onRecapClick = {},
        )
    }
}
