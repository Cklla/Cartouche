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
 * - `platform` : la nouvelle liste est d'abord bornée, puis remplacée **seulement si** les cases
 *   « joué sur » déjà cochées retrouvent toutes leur plateforme (`realignPlayedPlatformsOrNull`).
 * - `estimatedPlaytime*` : seules les durées qu'IGDB fournit (non nulles) sont écrites ; les autres
 *   sont gardées.
 * - `releaseYear` : renseignée si elle est vide.
 * - `igdbLookupAttempted` : passe à `true`, y compris quand [match] est `null` (IgdbLookup.NotFound :
 *   IGDB ne connaît pas ce jeu, inutile de le relancer en boucle). Un échec de lecture (réseau) n'appelle
 *   pas cette règle : la tentative n'est alors pas consommée (voir `DetailViewModel`).
 * - Un [match] sans `igdbId` (rapprochement par nom non certain) n'écrit **ni id, ni jaquette, ni
 *   année** : seules les plateformes et les durées sont reprises.
 *
 * **Jamais modifiés** : titre, genre, statut, note, heures personnelles, notes, dates, plateformes
 * jouées (hors réalignement de casse).
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
        ?: return enriched
    return enriched.copy(platform = newPlatform, playedPlatforms = realignedPlayedPlatforms)
}
