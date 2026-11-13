package fr.cklla.cartouche.data.remote.igdb.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Un jeu IGDB, tel que renvoyé par l'endpoint `POST /v4/games`. Tous les champs hors `id` et `name`
 * peuvent être absents : IGDB n'en renseigne qu'une partie selon le jeu (29 jeux sur ~130 sans
 * jaquette, 15 sur ~100 sans date lors de l'exploration), et un champ absent ne doit jamais faire
 * écarter un jeu (voir `IgdbGameMerge`).
 *
 * - `firstReleaseDate` (timestamp Unix en secondes) : année affichée, et signal pour départager des
 *   candidats au nom proche (voir `IgdbMappers.findBestMatch`) ;
 * - `gameType`, `versionParent`, `parentGame` : types et liens servant à rattacher une édition ou un
 *   portage à son jeu de base (voir `IgdbGameMerge`) ;
 * - `totalRatingCount` : nombre de notes, critère de tri des résultats.
 */
@JsonClass(generateAdapter = true)
data class IgdbGameDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    @Json(name = "platforms") val platforms: List<IgdbPlatformDto>? = null,
    @Json(name = "cover") val cover: IgdbCoverDto? = null,
    @Json(name = "genres") val genres: List<IgdbGenreDto>? = null,
    @Json(name = "game_type") val gameType: IgdbGameTypeDto? = null,
    @Json(name = "version_parent") val versionParent: IgdbRefDto? = null,
    @Json(name = "parent_game") val parentGame: IgdbRefDto? = null,
    @Json(name = "total_rating_count") val totalRatingCount: Int? = null,
)

/** Jaquette IGDB (`games.cover`) : seul `image_id` est lu, l'URL est construite par notre code. */
@JsonClass(generateAdapter = true)
data class IgdbCoverDto(
    @Json(name = "image_id") val imageId: String? = null,
)

/** Genre IGDB (`games.genres`). */
@JsonClass(generateAdapter = true)
data class IgdbGenreDto(
    @Json(name = "name") val name: String,
)

/** Type de jeu IGDB (`games.game_type`, table `game_types`) : 0 jeu de base, 1 DLC, 8 remake... */
@JsonClass(generateAdapter = true)
data class IgdbGameTypeDto(
    @Json(name = "id") val id: Long,
)

/** Référence vers un autre jeu (`version_parent`, `parent_game`) : seul l'id nous intéresse. */
@JsonClass(generateAdapter = true)
data class IgdbRefDto(
    @Json(name = "id") val id: Long,
)

/** Une plateforme IGDB (`games.platforms`), voir `IgdbGameDto.platforms`. */
@JsonClass(generateAdapter = true)
data class IgdbPlatformDto(
    @Json(name = "name") val name: String,
)

/**
 * Durée de vie d'un jeu IGDB (`POST /v4/game_time_to_beats`), en secondes. Les trois champs sont
 * tous affichés dans l'app quand ils sont renseignés (voir `Game.estimatedPlaytime*Hours`).
 */
@JsonClass(generateAdapter = true)
data class IgdbTimeToBeatDto(
    @Json(name = "hastily") val hastilySeconds: Int? = null,
    @Json(name = "normally") val normallySeconds: Int? = null,
    @Json(name = "completely") val completelySeconds: Int? = null,
)

/** Réponse du flow OAuth2 client credentials de Twitch (`POST id.twitch.tv/oauth2/token`). */
@JsonClass(generateAdapter = true)
data class TwitchTokenResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresInSeconds: Long,
)
