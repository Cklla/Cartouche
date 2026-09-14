package fr.cklla.cartouche.domain.repository

/**
 * Recherche du temps de jeu estimé ("temps pour terminer normalement") d'un jeu via IGDB.
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local, il ne fait que
 * répondre à une question ponctuelle ("combien d'heures pour ce titre ?"). C'est au ViewModel
 * appelant (voir `DetailViewModel`) de décider quand l'interroger et de mettre le résultat en
 * cache dans le backlog via [GameRepository].
 */
interface IgdbPlaytimeRepository {

    /**
     * Cherche [title] sur IGDB et renvoie le temps de jeu estimé en heures, ou `null` si aucune
     * correspondance fiable n'est trouvée, si IGDB n'a pas la donnée, ou en cas d'échec (réseau,
     * authentification, quota...) — jamais d'exception qui remonterait jusqu'à l'UI.
     */
    suspend fun findEstimatedPlaytimeHours(title: String): Int?
}
