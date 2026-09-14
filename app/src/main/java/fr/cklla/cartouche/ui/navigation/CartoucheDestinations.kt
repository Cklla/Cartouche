package fr.cklla.cartouche.ui.navigation

import fr.cklla.cartouche.ui.AppTab

/**
 * Routes de navigation de l'app (Navigation Compose).
 *
 * Les 3 onglets (Bibliothèque/Recherche/Stats) sont des destinations de premier
 * niveau, empilées une seule fois grâce à `popUpTo`/`restoreState` dans
 * `MainActivity`. Détail est poussé par-dessus depuis la Bibliothèque et n'a pas
 * de barre de navigation basse (voir maquette : écran "empilé/push").
 */
object CartoucheDestinations {
    const val BIBLIOTHEQUE = "bibliotheque"
    const val RECHERCHE = "recherche"
    const val STATS = "stats"

    const val DETAIL_ARG_GAME_ID = "gameId"
    const val DETAIL = "detail/{$DETAIL_ARG_GAME_ID}"

    fun detailRoute(gameId: Long) = "detail/$gameId"
}

/** Route associée à chaque onglet de la navigation basse. */
val AppTab.route: String
    get() = when (this) {
        AppTab.BIBLIOTHEQUE -> CartoucheDestinations.BIBLIOTHEQUE
        AppTab.RECHERCHE -> CartoucheDestinations.RECHERCHE
        AppTab.STATS -> CartoucheDestinations.STATS
    }
