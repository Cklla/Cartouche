package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus

/**
 * Données affichées par l'écran Stats, calculées à partir du backlog complet.
 *
 * `countsByStatus` couvre toujours les 4 [GameStatus] (0 si aucun jeu dans ce statut) :
 * c'est ce qui alimente à la fois l'anneau de progression et sa légende.
 */
data class StatsData(
    val completedCount: Int = 0,
    val backlogSize: Int = 0,
    val totalHoursPlayed: Int = 0,
    val completionPercent: Int = 0,
    val countsByStatus: Map<GameStatus, Int> = GameStatus.entries.associateWith { 0 },
)

/**
 * Logique de calcul extraite du ViewModel pour rester testable en pur Kotlin.
 *
 * Le pourcentage de complétion suit la même formule que le prototype
 * (`Math.round(termineCount / totalCount * 100)`, 0 si le backlog est vide).
 */
fun computeStats(games: List<Game>): StatsData {
    val backlogSize = games.size
    val completedCount = games.count { it.status == GameStatus.TERMINE }
    val completionPercent = if (backlogSize == 0) {
        0
    } else {
        Math.round(completedCount * 100f / backlogSize)
    }

    return StatsData(
        completedCount = completedCount,
        backlogSize = backlogSize,
        totalHoursPlayed = games.sumOf { it.hoursPlayed },
        completionPercent = completionPercent,
        countsByStatus = GameStatus.entries.associateWith { status -> games.count { it.status == status } },
    )
}
