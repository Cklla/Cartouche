package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(stats = uiState, modifier = modifier)
}

@Composable
private fun StatsContent(stats: StatsData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatCardsGrid(stats = stats)
            DonutSection(stats = stats)
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
            text = stringResource(R.string.stats_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.stats_title),
            style = CartoucheTextStyles.screenTitle,
            color = TextPrimary,
        )
    }
}

@Composable
private fun StatCardsGrid(stats: StatsData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                value = stats.completedCount.toString(),
                label = stringResource(R.string.stats_completed_label),
                valueColor = SuccessGreen,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = stats.backlogSize.toString(),
                label = stringResource(R.string.stats_backlog_size_label),
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f),
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

/**
 * Anneau de progression, équivalent natif du `conic-gradient` CSS du prototype :
 * un segment par statut (dans l'ordre de [GameStatus.entries]), proportionnel à
 * son nombre de jeux, dessiné en partant du haut (`startAngle = -90f`) dans le
 * sens horaire — même convention que le prototype.
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

            if (stats.backlogSize == 0) {
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
                GameStatus.entries.forEach { status ->
                    val count = stats.countsByStatus[status] ?: 0
                    val sweep = 360f * count / stats.backlogSize
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
    }
}

@Composable
private fun LegendList(stats: StatsData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameStatus.entries.forEach { status ->
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

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentPreview() {
    val games = listOf(
        GameStatus.A_FAIRE, GameStatus.A_FAIRE, GameStatus.A_FAIRE,
        GameStatus.EN_COURS, GameStatus.EN_COURS,
        GameStatus.TERMINE, GameStatus.TERMINE, GameStatus.TERMINE, GameStatus.TERMINE,
    ).mapIndexed { index, status ->
        fr.cklla.cartouche.domain.model.Game(
            id = index.toLong(),
            title = "Jeu $index",
            platform = "PC",
            genre = "Aventure",
            status = status,
            hoursPlayed = index * 5,
        )
    }
    CartoucheTheme {
        StatsContent(stats = computeStats(games))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun StatsContentEmptyPreview() {
    CartoucheTheme {
        StatsContent(stats = StatsData())
    }
}
