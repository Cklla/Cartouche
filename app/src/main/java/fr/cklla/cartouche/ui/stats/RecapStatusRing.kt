package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.palette
import java.util.Locale

private val RING_STROKE = 23.dp

/** Tranche de l'anneau : un statut, l'angle où elle commence (0° = droite, -90° = haut) et son ouverture. */
internal data class StatusRingSegment(val status: GameStatus, val startAngle: Float, val sweepAngle: Float)

/**
 * Tranches de l'anneau Terminé / Abandonné, proportionnelles aux effectifs, depuis le haut dans le
 * sens horaire (même convention que l'anneau des Statistiques). Un effectif nul n'a pas de tranche.
 */
internal fun statusRingSegments(completedCount: Int, abandonedCount: Int): List<StatusRingSegment> {
    val total = completedCount + abandonedCount
    if (total <= 0) return emptyList()
    var startAngle = -90f
    return listOf(GameStatus.TERMINE to completedCount, GameStatus.ABANDONNE to abandonedCount)
        .filter { (_, count) -> count > 0 }
        .map { (status, count) ->
            val sweep = 360f * count / total
            StatusRingSegment(status, startAngle, sweep).also { startAngle += sweep }
        }
}

/**
 * Part de [segment] déjà dessinée quand l'anneau est révélé à hauteur de [revealProgress] (0 à 1) :
 * le balayage va de 0° à 360° depuis le haut, dans le sens horaire, et chaque tranche apparaît à
 * mesure que le balayage l'atteint.
 */
internal fun revealedSweep(segment: StatusRingSegment, revealProgress: Float): Float {
    val sweptAngle = 360f * revealProgress
    val segmentOffset = segment.startAngle + 90f
    return (sweptAngle - segmentOffset).coerceIn(0f, segment.sweepAngle)
}

/**
 * Anneau Terminé / Abandonné du récap : même tracé que celui des Statistiques, révélé par un
 * balayage circulaire à l'entrée de la slide ; le total, au centre, compte de 0 à la somme.
 * Le dessin est décoratif : la légende juste dessous porte les mêmes effectifs en texte.
 */
@Composable
internal fun StatusRing(
    completedCount: Int,
    abandonedCount: Int,
    clock: RecapEntryClock,
    modifier: Modifier = Modifier,
    ringSize: Dp = 150.dp,
) {
    val segments = remember(completedCount, abandonedCount) { statusRingSegments(completedCount, abandonedCount) }
    // Couleurs résolues une fois, hors du bloc de dessin, qui ne doit rien allouer à chaque image.
    val colors = remember(segments) { segments.map { it.status.palette().color } }
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val strokeWidthPx = RING_STROKE.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val arcSize = Size(diameter, diameter)
            // Lu ici, au dessin : l'animation ne recompose rien.
            val reveal = clock.progress(RecapMotion.RING_DELAY, RecapMotion.RING_DURATION)
            segments.forEachIndexed { index, segment ->
                val sweep = revealedSweep(segment, reveal)
                if (sweep > 0f) {
                    drawArc(
                        color = colors[index],
                        startAngle = segment.startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUpText(
                target = completedCount + abandonedCount,
                clock = clock,
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
