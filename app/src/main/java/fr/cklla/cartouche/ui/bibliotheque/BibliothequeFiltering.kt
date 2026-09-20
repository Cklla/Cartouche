package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.ui.completedYear

/**
 * Logique de filtrage/comptage extraite du ViewModel et des Composables pour
 * rester testable en pur Kotlin, sans dépendance Android.
 */

/**
 * [selectedYear] ne s'applique qu'au filtre "Terminé" : sur les autres filtres, un jeu ne passe
 * pas forcément (encore) par ce statut, filtrer par année de complétion n'aurait pas de sens.
 */
fun filterGames(games: List<Game>, filter: BacklogFilter, selectedYear: Int? = null): List<Game> =
    games.filter { filter.matches(it.status) }
        .filter { filter != BacklogFilter.TERMINE || selectedYear == null || completedYear(it) == selectedYear }

fun countByFilter(games: List<Game>): Map<BacklogFilter, Int> =
    BacklogFilter.entries.associateWith { filter -> games.count { filter.matches(it.status) } }
