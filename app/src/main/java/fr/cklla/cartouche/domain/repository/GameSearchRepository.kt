package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.Resource

/**
 * Point d'accès à la recherche de jeux via l'API externe (RAWG).
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local,
 * il ne fait qu'interroger RAWG. C'est le ViewModel de l'écran Recherche qui
 * orchestre les deux (recherche RAWG + ajout au backlog via [GameRepository]).
 */
interface GameSearchRepository {

    /** Recherche des jeux par titre. Une requête vide renvoie une liste vide sans appel réseau. */
    suspend fun searchGames(query: String): Resource<List<GameSearchResult>>
}
