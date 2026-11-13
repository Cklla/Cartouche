package fr.cklla.cartouche.domain.model

import java.net.URI

/** Hôte des anciennes jaquettes RAWG, remplacées par celles d'IGDB quand IGDB en a une. */
private const val RAWG_COVER_HOST = "media.rawg.io"

/** Vrai si [url] est hébergée exactement sur `media.rawg.io` (hôte exact, jamais un sous-domaine ni un faux ami). */
private fun isRawgCoverUrl(url: String): Boolean =
    runCatching { URI(url).host.equals(RAWG_COVER_HOST, ignoreCase = true) }.getOrDefault(false)

/**
 * Règle d'enrichissement IGDB : ce qu'IGDB peut compléter ou corriger sur un jeu déjà connu. C'est
 * l'unique endroit où cette règle est écrite pour l'app ; la même est portée à l'identique côté web,
 * avec les mêmes cas de test.
 *
 * - `igdbId` : renseigné s'il est vide, jamais changé ensuite.
 * - `coverUrl` : remplacée par la jaquette IGDB **si IGDB en a une** et si la valeur actuelle est
 *   vide ou hébergée sur `media.rawg.io` (captures paysage). Une jaquette choisie ailleurs reste.
 * - `platform` : la nouvelle liste est d'abord bornée, puis le réalignement des cases « joué sur »
 *   déjà cochées est tenté **en premier** (`realignPlayedPlatformsOrNull`) : si toutes retrouvent leur
 *   plateforme (ou si aucune n'est cochée), la liste remplace `platform` et les cases suivent.
 *   Sinon, exception pour un jeu dont la plateforme **enregistrée** est unique (0 ou 1 nom) **et** dont
 *   la plateforme jouée n'est que la copie de cette plateforme (à la casse près) : aucune case n'existe
 *   alors à l'écran, ce n'est pas un choix de l'utilisateur. Si IGDB est certain (`igdbId` présent),
 *   `platform` est corrigée et la plateforme jouée suit (la nouvelle plateforme si elle est unique,
 *   sinon aucune case cochée). Dans tous les autres cas (case cochée explicitement, rapprochement non
 *   certain), `platform` et les cases sont gardées telles quelles.
 * - `estimatedPlaytime*` : seules les durées qu'IGDB fournit (non nulles) sont écrites ; les autres
 *   sont gardées.
 * - `releaseYear` : renseignée si elle est vide.
 * - `igdbLookupAttempted` : passe à `true`, y compris quand [match] est `null` (IgdbLookup.NotFound :
 *   IGDB ne connaît pas ce jeu, inutile de le relancer en boucle). Un échec de lecture (réseau) n'appelle
 *   pas cette règle : la tentative n'est alors pas consommée (voir `DetailViewModel`).
 * - Un [match] sans `igdbId` (rapprochement par nom non certain) n'écrit **ni id, ni jaquette, ni
 *   année** : seules les plateformes et les durées sont reprises.
 *
 * **Jamais modifiés** : titre, genre, statut, note, heures personnelles, notes, dates. Les plateformes
 * jouées ne changent que par le réalignement de casse ou par l'exception du jeu à plateforme unique.
 */
fun applyIgdbEnrichment(game: Game, match: IgdbGameMatch?): Game {
    val attempted = game.copy(igdbLookupAttempted = true)
    if (match == null) return attempted

    val currentCover = game.coverUrl
    val newCover = match.coverUrl?.takeIf { currentCover.isNullOrEmpty() || isRawgCoverUrl(currentCover) }

    val enriched = attempted.copy(
        igdbId = game.igdbId ?: igdbIdOrNull(match.igdbId),
        coverUrl = newCover ?: currentCover,
        releaseYear = game.releaseYear ?: releaseYearOrNull(match.releaseYear),
        estimatedPlaytimeHastilyHours =
            match.playtimeEstimate?.hastilyHours ?: game.estimatedPlaytimeHastilyHours,
        estimatedPlaytimeNormallyHours =
            match.playtimeEstimate?.normallyHours ?: game.estimatedPlaytimeNormallyHours,
        estimatedPlaytimeCompletelyHours =
            match.playtimeEstimate?.completelyHours ?: game.estimatedPlaytimeCompletelyHours,
    )

    val newPlatform = match.platform
        ?.let { joinPlatformsWithinLimit(parsePlatforms(it)) }
        ?.takeIf { it.isNotEmpty() }
        ?: return enriched
    val realignedPlayedPlatforms = realignPlayedPlatformsOrNull(enriched.playedPlatforms, newPlatform)
    if (realignedPlayedPlatforms != null) {
        return enriched.copy(platform = newPlatform, playedPlatforms = realignedPlayedPlatforms)
    }
    // Jeu à plateforme unique : aucune case « joué sur » n'existe à l'écran, la plateforme jouée n'est
    // donc pas un choix de l'utilisateur mais la simple copie de `platform`. Si IGDB est certain
    // (id connu), elle se corrige avec la plateforme (ex. un remake enregistré sur Switch alors
    // qu'il n'existe que sur Switch 2). On ne l'applique que si la plateforme jouée est bien cette
    // copie : une plateforme jouée différente (ex. cochée avant que la liste ne se réduise) est un
    // vrai choix, on la garde, et la plateforme avec.
    val registered = parsePlatforms(game.platform)
    val platformIsDerived = registered.size <= 1 &&
        enriched.playedPlatforms.all { played -> registered.any { it.equals(played, ignoreCase = true) } }
    if (!platformIsDerived || match.igdbId == null) return enriched
    return enriched.copy(
        platform = newPlatform,
        playedPlatforms = parsePlatforms(newPlatform).singleOrNull()?.let(::setOf).orEmpty(),
    )
}
