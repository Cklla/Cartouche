package fr.cklla.cartouche.domain.model

/**
 * Un résultat de recherche RAWG, avant ajout au backlog.
 *
 * Distinct de [Game] : ce modèle ne représente qu'une fiche de la base RAWG,
 * pas encore un jeu possédé (pas de statut, de note ni de temps de jeu).
 */
data class GameSearchResult(
    val rawgId: Long,
    val title: String,
    val platform: String,
    val genre: String,
    val year: String,
    val coverUrl: String?,
)

/**
 * Convertit un résultat de recherche en jeu du backlog, avec le statut par défaut "À faire".
 *
 * Les champs `estimatedPlaytime*` ne sont jamais renseignés ici : RAWG ne fournit plus cette
 * donnée (trop peu fiable, voir historique du champ). Ils sont recherchés séparément via IGDB,
 * seulement à l'ouverture de la fiche détail (voir `DetailViewModel`).
 */
fun GameSearchResult.toGame(): Game = Game(
    title = title,
    platform = platform,
    genre = genre,
    status = GameStatus.A_FAIRE,
    coverUrl = coverUrl,
)
