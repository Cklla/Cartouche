package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.availableCompletedYears
import fr.cklla.cartouche.ui.completedYear

/**
 * Données affichées par l'écran Stats, calculées soit sur le backlog complet
 * ([selectedYear] `null`), soit sur les seuls jeux terminés une année précise.
 *
 * `countsByStatus` couvre toujours les 4 [GameStatus] (0 si aucun jeu dans ce statut) :
 * c'est ce qui alimente à la fois l'anneau de progression et sa légende en vue "toutes années".
 * `backlogSize`/`completionPercent` n'ont de sens qu'en vue "toutes années" — l'UI ne les affiche
 * pas quand [selectedYear] est renseigné (voir `StatsScreen`).
 */
data class StatsData(
    val completedCount: Int = 0,
    val backlogSize: Int = 0,
    val totalHoursPlayed: Int = 0,
    val completionPercent: Int = 0,
    val countsByStatus: Map<GameStatus, Int> = GameStatus.entries.associateWith { 0 },
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int? = null,
)

/**
 * Logique de calcul extraite du ViewModel pour rester testable en pur Kotlin.
 *
 * Vue "toutes années" ([selectedYear] `null`) : comportement historique, pourcentage de
 * complétion selon la même formule que le prototype (`Math.round(termineCount / totalCount *
 * 100)`, 0 si le backlog est vide).
 *
 * Vue "année sélectionnée" : ne porte que sur les jeux Terminé cette année-là (voir
 * `completedYear`) — "taille du backlog", "À faire"/"En cours" et le pourcentage n'ont plus de
 * sens une fois filtré sur une seule année (voir `StatsScreen`, qui masque ces éléments).
 */
fun computeStats(games: List<Game>, selectedYear: Int? = null): StatsData {
    val availableYears = availableCompletedYears(games)

    if (selectedYear == null) {
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
            totalHoursPlayed = games.sumOf { it.userPlaytimeHours },
            completionPercent = completionPercent,
            countsByStatus = GameStatus.entries.associateWith { status -> games.count { it.status == status } },
            availableYears = availableYears,
        )
    }

    val completedThisYear = games.filter { it.status == GameStatus.TERMINE && completedYear(it) == selectedYear }
    return StatsData(
        completedCount = completedThisYear.size,
        totalHoursPlayed = completedThisYear.sumOf { it.userPlaytimeHours },
        countsByStatus = GameStatus.entries.associateWith { status ->
            if (status == GameStatus.TERMINE) completedThisYear.size else 0
        },
        availableYears = availableYears,
        selectedYear = selectedYear,
    )
}
