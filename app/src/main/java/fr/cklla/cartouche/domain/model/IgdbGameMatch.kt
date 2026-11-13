package fr.cklla.cartouche.domain.model

/**
 * Ce qu'IGDB sait d'un jeu, une fois son id résolu (connu d'avance ou retrouvé par nom et année,
 * voir `IgdbPlaytimeRepository`). Sert d'entrée à la règle d'enrichissement unique
 * (`applyIgdbEnrichment`), qui décide quelles données du jeu sont mises à jour.
 *
 * Tous les champs hors [igdbId] sont indépendamment nullables : IGDB peut ne renseigner qu'une
 * partie des informations (jaquette, date, plateformes, durées). Ce type est `null` dans son
 * ensemble uniquement quand le jeu n'a pas pu être identifié ou lu.
 *
 * @param igdbId identifiant IGDB du jeu, dans la borne acceptée par le serveur.
 * @param platform plateformes IGDB déjà formatées comme `Game.platform` (jointes par "/", triées,
 *   éditions et portages fusionnés) ; `null` si IGDB n'en a aucune — jamais une chaîne vide. C'est
 *   la règle d'enrichissement qui décide si ce remplacement est sûr vis-à-vis de
 *   `Game.playedPlatforms` (voir `realignPlayedPlatformsOrNull`).
 * @param coverUrl jaquette HTTPS sur `images.igdb.com`, construite par notre code (voir
 *   `IgdbGameMerge.buildCoverUrl`) ; `null` si IGDB n'en a pas.
 * @param releaseYear année de sortie IGDB, `null` si IGDB n'a pas de date.
 */
data class IgdbGameMatch(
    val igdbId: Long,
    val platform: String? = null,
    val coverUrl: String? = null,
    val releaseYear: Int? = null,
    val playtimeEstimate: IgdbPlaytimeEstimate? = null,
)
