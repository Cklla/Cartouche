package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game

data class BibliothequeUiState(
    val isLoading: Boolean = true,
    val visibleGames: List<Game> = emptyList(),
    val selectedFilter: BacklogFilter = BacklogFilter.TOUS,
    val filterCounts: Map<BacklogFilter, Int> = emptyMap(),
)
