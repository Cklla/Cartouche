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
 * @param igdbId identifiant IGDB du jeu, dans la borne acceptée par le serveur ; `null` quand le
 *   jeu a seulement été **rapproché par nom** sans certitude (titre ou année différents) : seules les
 *   plateformes et les durées sont alors lues, jamais l'id ni la jaquette (voir
 *   `IgdbPlaytimeRepositoryImpl`).
 * @param platform plateformes IGDB déjà formatées comme `Game.platform` (jointes par "/", triées,
 *   éditions et portages fusionnés) ; `null` si IGDB n'en a aucune — jamais une chaîne vide. C'est
 *   la règle d'enrichissement qui décide si ce remplacement est sûr vis-à-vis de
 *   `Game.playedPlatforms` (voir `realignPlayedPlatformsOrNull`).
 * @param coverUrl jaquette HTTPS sur `images.igdb.com`, construite par notre code (voir
 *   `IgdbGameMerge.buildCoverUrl`) ; `null` si IGDB n'en a pas.
 * @param releaseYear année de sortie IGDB, `null` si IGDB n'a pas de date.
 */
data class IgdbGameMatch(
    val igdbId: Long?,
    val platform: String? = null,
    val coverUrl: String? = null,
    val releaseYear: Int? = null,
    val playtimeEstimate: IgdbPlaytimeEstimate? = null,
)

/**
 * Issue d'une lecture IGDB, à trois états : la différence entre « IGDB ne connaît pas ce jeu » et
 * « la lecture a échoué » décide si la tentative est consommée (voir `DetailViewModel`).
 */
sealed interface IgdbLookup {
    /** Le jeu a été identifié et lu. */
    data class Found(val match: IgdbGameMatch) : IgdbLookup

    /** IGDB a répondu, mais aucun jeu fiable ne correspond : définitif, inutile de recommencer. */
    data object NotFound : IgdbLookup

    /** La lecture a échoué (hors ligne, quota, jeton indisponible) : à retenter plus tard. */
    data object Failed : IgdbLookup
}
