package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate

/**
 * Recherche du temps de jeu estimé (durées "rapide"/"normal"/"complet") d'un jeu via IGDB.
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local, il ne fait que
 * répondre à une question ponctuelle ("combien d'heures pour ce titre ?"). C'est au ViewModel
 * appelant (voir `DetailViewModel`) de décider quand l'interroger et de mettre le résultat en
 * cache dans le backlog via [GameRepository].
 */
interface IgdbPlaytimeRepository {

    /**
     * Cherche [title] sur IGDB et renvoie les temps de jeu estimés trouvés, ou `null` si aucune
     * correspondance fiable n'est trouvée, ou en cas d'échec (réseau, authentification,
     * quota...) — jamais d'exception qui remonterait jusqu'à l'UI. Un résultat non nul peut
     * malgré tout avoir des champs individuellement `null` si IGDB n'a pas cette donnée précise
     * pour ce jeu (voir [IgdbPlaytimeEstimate]).
     */
    suspend fun findEstimatedPlaytime(title: String): IgdbPlaytimeEstimate?
}
