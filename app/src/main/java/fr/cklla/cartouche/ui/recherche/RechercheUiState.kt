package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.domain.model.GameSearchResult

data class RechercheUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GameSearchResult> = emptyList(),
    val errorMessage: String? = null,
    /** Titres du backlog (normalisés, voir [normalizeTitle]), pour griser les résultats déjà ajoutés. */
    val backlogTitles: Set<String> = emptySet(),
)
