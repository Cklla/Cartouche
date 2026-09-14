package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.Game

data class DetailUiState(
    val isLoading: Boolean = true,
    val game: Game? = null,
) {
    /**
     * Vrai une fois le jeu réellement présent dans le backlog (id non vide). Faux quand la fiche
     * est ouverte en aperçu depuis un résultat de Recherche pas encore ajouté (voir
     * `DetailViewModel`) : dans ce cas, `DetailScreen` masque statut/note perso/temps de jeu
     * perso/notes libres et affiche un bouton "Ajouter" à la place.
     */
    val isInBacklog: Boolean get() = !game?.id.isNullOrEmpty()
}
