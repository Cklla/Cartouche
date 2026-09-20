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
 * Les champs `estimatedPlaytime*` ne sont jamais renseignés ici : RAWG ne fournit pas cette
 * donnée. Ils sont recherchés séparément via IGDB, seulement à l'ouverture de la fiche détail
 * (voir `DetailViewModel`).
 *
 * `rawgId` et `releaseYear` sont en revanche conservés : ils ne servent à rien pour l'affichage,
 * mais permettent de fiabiliser la correspondance IGDB au moment de cette recherche (voir
 * `IgdbPlaytimeRepository`).
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
    rawgId = rawgId,
    releaseYear = year.toIntOrNull(),
    coverUrl = coverUrl,
    playedPlatforms = parsePlatforms(platform).singleOrNull()?.let(::setOf).orEmpty(),
)
