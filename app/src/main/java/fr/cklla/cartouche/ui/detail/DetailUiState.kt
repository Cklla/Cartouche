package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.completedYear

data class DetailUiState(
    val isLoading: Boolean = true,
    val game: Game? = null,
    /** Années proposées par le choix d'année de fin ou d'abandon (récente d'abord) ; vide hors [canEditStatusYear]. */
    val statusYearChoices: List<Int> = emptyList(),
) {
    /**
     * Vrai une fois le jeu réellement présent dans le backlog (id non vide). Faux quand la fiche
     * est ouverte en aperçu depuis un résultat de Recherche pas encore ajouté (voir
     * `DetailViewModel`) : dans ce cas, `DetailScreen` masque statut/note perso/temps de jeu
     * perso/notes libres et affiche un bouton "Ajouter" à la place.
     */
    val isInBacklog: Boolean get() = !game?.id.isNullOrEmpty()

    /** Vrai quand la ligne « Terminé en … » / « Abandonné en … » est affichée : jeu dans le backlog, Terminé ou Abandonné. */
    val canEditStatusYear: Boolean get() = game.canEditStatusYear()

    /**
     * Année de fin (jeu Terminé) ou d'abandon (jeu Abandonné) enregistrée, ou `null` si inconnue
     * (jeu passé à ce statut avant que la date soit conservée) ou si le statut n'est pas daté.
     */
    val statusYear: Int?
        get() = game?.let {
            when (it.status) {
                GameStatus.TERMINE -> completedYear(it)
                GameStatus.ABANDONNE -> abandonedYear(it)
                else -> null
            }
        }
}

/** Seul un jeu réellement dans le backlog et au statut Terminé ou Abandonné a une année modifiable. */
internal fun Game?.canEditStatusYear(): Boolean =
    this != null && id.isNotEmpty() && (status == GameStatus.TERMINE || status == GameStatus.ABANDONNE)
