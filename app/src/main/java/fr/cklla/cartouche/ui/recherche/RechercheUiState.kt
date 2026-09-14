package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.domain.model.GameSearchResult

data class RechercheUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GameSearchResult> = emptyList(),
    val errorMessage: String? = null,
    /**
     * Id du backlog par titre normalisé (voir [normalizeTitle]) : sert à la fois à griser les
     * résultats déjà ajoutés et à retrouver l'id du jeu quand on ouvre sa fiche depuis un résultat
     * déjà présent dans le backlog (voir [backlogGameId]).
     */
    val backlogGameIdsByTitle: Map<String, String> = emptyMap(),
)
