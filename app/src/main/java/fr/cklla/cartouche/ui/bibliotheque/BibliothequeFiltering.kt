package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.availableAbandonedYears
import fr.cklla.cartouche.ui.availableCompletedYears
import fr.cklla.cartouche.ui.completedYear

/**
 * Logique de filtrage/comptage extraite du ViewModel et des Composables pour
 * rester testable en pur Kotlin, sans dépendance Android.
 */

/**
 * [selectedYear] ne s'applique que sous "Terminé" (année de complétion) et "Abandonné" (année
 * d'abandon) : sur les autres filtres, un jeu ne passe pas forcément (encore) par l'un de ces deux
 * statuts, filtrer par année n'aurait pas de sens.
 */
fun filterGames(games: List<Game>, filter: BacklogFilter, selectedYear: Int? = null): List<Game> =
    games.filter { filter.matches(it.status) }
        .filter { selectedYear == null || matchesYear(it, filter, selectedYear) }

private fun matchesYear(game: Game, filter: BacklogFilter, selectedYear: Int): Boolean = when (filter) {
    BacklogFilter.TERMINE -> completedYear(game) == selectedYear
    BacklogFilter.ABANDONNE -> abandonedYear(game) == selectedYear
    else -> true
}

/** Années disponibles pour le filtre par année, selon l'onglet de statut actif. */
fun availableYearsFor(filter: BacklogFilter, games: List<Game>): List<Int> = when (filter) {
    BacklogFilter.TERMINE -> availableCompletedYears(games)
    BacklogFilter.ABANDONNE -> availableAbandonedYears(games)
    else -> emptyList()
}

fun countByFilter(games: List<Game>): Map<BacklogFilter, Int> =
    BacklogFilter.entries.associateWith { filter -> games.count { filter.matches(it.status) } }
