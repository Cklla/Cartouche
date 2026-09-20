package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate

/**
 * Recherche via IGDB des informations complémentaires à RAWG pour un jeu : temps de jeu estimé
 * (durées "rapide"/"normal"/"complet") et liste de plateformes plus fiable (voir [IgdbGameMatch]).
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local, il ne fait que
 * répondre à une question ponctuelle ("qu'est-ce qu'IGDB sait de ce titre ?"). C'est au ViewModel
 * appelant (voir `DetailViewModel`) de décider quand l'interroger et de mettre le résultat en
 * cache dans le backlog via [GameRepository].
 */
interface IgdbPlaytimeRepository {

    /**
     * Cherche le jeu IGDB correspondant et renvoie ce qui a pu en être tiré ([IgdbGameMatch]), ou
     * `null` si aucune correspondance fiable n'est trouvée, ou en cas d'échec (réseau,
     * authentification, quota...) — jamais d'exception qui remonterait jusqu'à l'UI. Un résultat
     * non nul peut malgré tout avoir des champs individuellement `null` si IGDB n'a pas cette
     * donnée précise pour ce jeu (voir [IgdbGameMatch]).
     *
     * La correspondance se fait en cascade (voir `IgdbMappers`) : [rawgId] permet de tenter une
     * correspondance fiable par App ID Steam en priorité ; [title] et [releaseYear] servent de
     * repli par similarité de nom si cette première méthode ne donne rien.
     */
    suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, rawgId: Long?): IgdbGameMatch?
}
