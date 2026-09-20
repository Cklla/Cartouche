package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game

data class BibliothequeUiState(
    val isLoading: Boolean = true,
    val visibleGames: List<Game> = emptyList(),
    val selectedFilter: BacklogFilter = BacklogFilter.TOUS,
    val filterCounts: Map<BacklogFilter, Int> = emptyMap(),
    /** Années disponibles pour le filtre par année de complétion, non vide seulement sous "Terminé". */
    val availableCompletedYears: List<Int> = emptyList(),
    val selectedYear: Int? = null,
)
