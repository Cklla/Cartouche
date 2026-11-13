package fr.cklla.cartouche.domain.model

/**
 * Un résultat de recherche IGDB, avant ajout au backlog.
 *
 * Distinct de [Game] : ce modèle ne représente qu'une fiche du catalogue IGDB, pas encore un jeu
 * possédé (pas de statut, de note ni de temps de jeu).
 *
 * @param igdbId identifiant IGDB du jeu de base (les éditions et portages sont fusionnés dans cette
 *   fiche, voir `IgdbGameMerge`). Toujours dans la borne acceptée par le serveur.
 */
data class GameSearchResult(
    val igdbId: Long,
    val title: String,
    val platform: String,
    val genre: String,
    val year: String,
    val coverUrl: String?,
)

/**
 * Convertit un résultat de recherche en jeu du backlog, avec le statut par défaut "À faire".
 *
 * Les champs `estimatedPlaytime*` ne sont jamais renseignés ici : ils sont lus séparément via
 * IGDB, à l'ouverture de la fiche détail (voir `DetailViewModel`).
 *
 * `igdbId` et `releaseYear` sont conservés : l'id permet de relire le jeu directement, sans
 * correspondance par nom.
 *
 * `playedPlatforms` démarre pré-rempli avec l'unique plateforme disponible quand `platform` n'en
 * liste qu'une seule (aucune case à cocher n'a de sens dans ce cas, voir `DetailScreen`) ; laissé
 * vide si plusieurs plateformes sont possibles, à renseigner manuellement par l'utilisateur.
 */
fun GameSearchResult.toGame(): Game = Game(
    title = title,
    platform = platform,
    genre = genre,
    status = GameStatus.A_FAIRE,
    igdbId = igdbIdOrNull(igdbId),
    releaseYear = releaseYearOrNull(year.toIntOrNull()),
    coverUrl = coverUrl,
    playedPlatforms = parsePlatforms(platform).singleOrNull()?.let(::setOf).orEmpty(),
)
