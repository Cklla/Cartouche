package fr.cklla.cartouche.ui.bibliotheque

import androidx.annotation.StringRes
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameStatus

/** Filtre de statut appliqué à la Bibliothèque ("Tous" en plus des 4 [GameStatus]). */
enum class BacklogFilter(@param:StringRes val labelRes: Int) {
    TOUS(R.string.filter_tous),
    A_FAIRE(R.string.filter_a_faire),
    EN_COURS(R.string.filter_en_cours),
    TERMINE(R.string.filter_termine),
    ABANDONNE(R.string.filter_abandonne),
}

fun BacklogFilter.matches(status: GameStatus): Boolean = when (this) {
    BacklogFilter.TOUS -> true
    BacklogFilter.A_FAIRE -> status == GameStatus.A_FAIRE
    BacklogFilter.EN_COURS -> status == GameStatus.EN_COURS
    BacklogFilter.TERMINE -> status == GameStatus.TERMINE
    BacklogFilter.ABANDONNE -> status == GameStatus.ABANDONNE
}
