package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game

/**
 * Logique de filtrage/comptage extraite du ViewModel et des Composables pour
 * rester testable en pur Kotlin, sans dépendance Android.
 */

fun filterGames(games: List<Game>, filter: BacklogFilter): List<Game> =
    games.filter { filter.matches(it.status) }

fun countByFilter(games: List<Game>): Map<BacklogFilter, Int> =
    BacklogFilter.entries.associateWith { filter -> games.count { filter.matches(it.status) } }
