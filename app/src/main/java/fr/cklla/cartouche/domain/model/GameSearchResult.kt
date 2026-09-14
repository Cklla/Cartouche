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
    /** Temps de jeu moyen constaté par RAWG, en heures ; `null` si RAWG n'a pas cette donnée. */
    val estimatedPlaytimeHours: Int? = null,
)

/** Convertit un résultat de recherche en jeu du backlog, avec le statut par défaut "À faire". */
fun GameSearchResult.toGame(): Game = Game(
    title = title,
    platform = platform,
    genre = genre,
    status = GameStatus.A_FAIRE,
    coverUrl = coverUrl,
    estimatedPlaytimeHours = estimatedPlaytimeHours,
)
