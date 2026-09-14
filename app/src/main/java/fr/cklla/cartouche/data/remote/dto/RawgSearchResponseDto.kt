package fr.cklla.cartouche.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Réponse de l'endpoint de recherche RAWG (`GET /api/games?search=...`). */
@JsonClass(generateAdapter = true)
data class RawgSearchResponseDto(
    @Json(name = "results") val results: List<RawgGameDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RawgGameDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "released") val released: String? = null,
    @Json(name = "background_image") val backgroundImage: String? = null,
    @Json(name = "platforms") val platforms: List<RawgPlatformWrapperDto>? = null,
    @Json(name = "genres") val genres: List<RawgGenreDto>? = null,
    // Moyenne communautaire (majoritairement Steam) en heures ; absent ou à 0 quand RAWG n'a pas
    // la donnée pour ce jeu (voir `normalizePlaytime`, qui traite les deux cas comme "inconnu").
    @Json(name = "playtime") val playtime: Int? = null,
)

@JsonClass(generateAdapter = true)
data class RawgPlatformWrapperDto(
    @Json(name = "platform") val platform: RawgPlatformDto,
)

@JsonClass(generateAdapter = true)
data class RawgPlatformDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
)

@JsonClass(generateAdapter = true)
data class RawgGenreDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
)
