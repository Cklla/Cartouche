package fr.cklla.cartouche.ui.navigation

import android.net.Uri
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.ui.AppTab
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter

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

    const val RECAP_ARG_YEAR = "year"
    const val RECAP = "recap/{$RECAP_ARG_YEAR}"

    fun recapRoute(year: Int) = "recap/$year"

    // Liste des jeux terminés/abandonnés d'une année, ouverte depuis RecapScreen en tapant sur le
    // nombre de jeux terminés ou abandonnés — réutilise `BacklogFilter`/`filterGames` de la
    // Bibliothèque plutôt qu'une nouvelle logique de filtrage.
    const val RECAP_GAMES_ARG_FILTER = "filter"
    const val RECAP_GAMES = "recap/{$RECAP_ARG_YEAR}/games/{$RECAP_GAMES_ARG_FILTER}"

    fun recapGamesRoute(year: Int, filter: BacklogFilter) = "recap/$year/games/${filter.name}"

    const val DETAIL_ARG_GAME_ID = "gameId"
    const val DETAIL = "detail/{$DETAIL_ARG_GAME_ID}"

    fun detailRoute(gameId: String) = "detail/$gameId"

    // Fiche d'un jeu pas encore ajouté au backlog, ouverte directement depuis un résultat de
    // Recherche : il n'y a pas encore d'id de backlog à relire en base, donc la fiche RAWG
    // complète transite par les paramètres de la route plutôt que par un id. Route distincte de
    // DETAIL ci-dessus (préfixe différent) pour qu'il n'y ait jamais d'ambiguïté de correspondance
    // entre les deux patterns dans le graphe de navigation.
    const val DETAIL_APERCU_ARG_RAWG_ID = "rawgId"
    const val DETAIL_APERCU_ARG_TITLE = "title"
    const val DETAIL_APERCU_ARG_PLATFORM = "platform"
    const val DETAIL_APERCU_ARG_GENRE = "genre"
    const val DETAIL_APERCU_ARG_YEAR = "year"
    const val DETAIL_APERCU_ARG_COVER_URL = "coverUrl"

    private const val DETAIL_APERCU_BASE = "apercu-jeu"
    const val DETAIL_APERCU = "$DETAIL_APERCU_BASE?" +
        "$DETAIL_APERCU_ARG_RAWG_ID={$DETAIL_APERCU_ARG_RAWG_ID}" +
        "&$DETAIL_APERCU_ARG_TITLE={$DETAIL_APERCU_ARG_TITLE}" +
        "&$DETAIL_APERCU_ARG_PLATFORM={$DETAIL_APERCU_ARG_PLATFORM}" +
        "&$DETAIL_APERCU_ARG_GENRE={$DETAIL_APERCU_ARG_GENRE}" +
        "&$DETAIL_APERCU_ARG_YEAR={$DETAIL_APERCU_ARG_YEAR}" +
        "&$DETAIL_APERCU_ARG_COVER_URL={$DETAIL_APERCU_ARG_COVER_URL}"

    fun detailApercuRoute(result: GameSearchResult): String {
        fun enc(value: String) = Uri.encode(value)
        return "$DETAIL_APERCU_BASE?" +
            "$DETAIL_APERCU_ARG_RAWG_ID=${result.rawgId}" +
            "&$DETAIL_APERCU_ARG_TITLE=${enc(result.title)}" +
            "&$DETAIL_APERCU_ARG_PLATFORM=${enc(result.platform)}" +
            "&$DETAIL_APERCU_ARG_GENRE=${enc(result.genre)}" +
            "&$DETAIL_APERCU_ARG_YEAR=${enc(result.year)}" +
            "&$DETAIL_APERCU_ARG_COVER_URL=${enc(result.coverUrl.orEmpty())}"
    }
}

/** Route associée à chaque onglet de la navigation basse. */
val AppTab.route: String
    get() = when (this) {
        AppTab.BIBLIOTHEQUE -> CartoucheDestinations.BIBLIOTHEQUE
        AppTab.RECHERCHE -> CartoucheDestinations.RECHERCHE
        AppTab.STATS -> CartoucheDestinations.STATS
    }
