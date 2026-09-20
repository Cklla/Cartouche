package fr.cklla.cartouche.domain.model

/**
 * Résultat d'une correspondance IGDB réussie pour un jeu (id IGDB retrouvé, voir
 * `IgdbPlaytimeRepository`) : regroupe le temps de jeu estimé et la liste de plateformes IGDB,
 * toutes deux résolues à partir du même id — un seul appel de correspondance (cascade ID Steam →
 * nom+année) sert aux deux usages plutôt que de la refaire séparément pour chacun.
 *
 * Les deux champs sont indépendamment nullables : l'id IGDB peut être résolu sans que
 * `game_time_to_beats` ait de donnée pour ce jeu ([playtimeEstimate] alors `null`), et
 * inversement IGDB peut n'avoir aucune plateforme renseignée ([platform] alors `null` — jamais une
 * chaîne vide, voir `IgdbMappers.formatIgdbPlatforms`). Ce type lui-même est `null` dans son
 * ensemble uniquement quand la correspondance a échoué (aucun id IGDB trouvé) ou qu'elle n'a rien
 * donné d'exploitable du tout.
 *
 * @param platform liste de plateformes IGDB déjà formatée comme `Game.platform` (jointe par "/",
 *   triée) — voir `IgdbMappers.formatIgdbPlatforms`. C'est au ViewModel appelant (voir
 *   `DetailViewModel`) de décider si ce remplacement est sûr vis-à-vis de `Game.playedPlatforms`
 *   déjà coché (voir `realignPlayedPlatformsOrNull`).
 */
data class IgdbGameMatch(
    val playtimeEstimate: IgdbPlaytimeEstimate?,
    val platform: String?,
)
