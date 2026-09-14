package fr.cklla.cartouche.data.remote.igdb.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Un jeu IGDB, tel que renvoyé par l'endpoint de recherche (`POST /v4/games`). */
@JsonClass(generateAdapter = true)
data class IgdbGameDto(
    @Json(name = "id") val id: Long,
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
