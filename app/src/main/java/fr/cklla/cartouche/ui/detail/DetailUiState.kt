package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.Game

data class DetailUiState(
    val isLoading: Boolean = true,
    val game: Game? = null,
)
