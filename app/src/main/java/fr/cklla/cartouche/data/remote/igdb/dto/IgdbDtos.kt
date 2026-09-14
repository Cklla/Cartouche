package fr.cklla.cartouche.data.remote.igdb.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Un jeu IGDB, tel que renvoyé par l'endpoint de recherche (`POST /v4/games`). `firstReleaseDate`
 * (timestamp Unix en secondes) sert de signal secondaire pour départager des candidats au nom
 * proche (voir `IgdbMappers.findBestMatch`).
 */
@JsonClass(generateAdapter = true)
data class IgdbGameDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
)

/**
 * Une entrée de l'endpoint `external_games` (`POST /v4/external_games`), qui relie un identifiant
 * externe (ex. App ID Steam) à un jeu IGDB. Seul le champ `game` nous intéresse : c'est l'id IGDB
 * du jeu correspondant (voir `IgdbMappers.buildSteamExternalGameQuery`).
 */
@JsonClass(generateAdapter = true)
data class IgdbExternalGameDto(
    @Json(name = "game") val gameId: Long,
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
