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

/** Réponse de l'endpoint des liens boutiques RAWG (`GET /api/games/{id}/stores`). */
@JsonClass(generateAdapter = true)
data class RawgStoreLinksResponseDto(
    @Json(name = "results") val results: List<RawgStoreLinkDto> = emptyList(),
)

/**
 * Un lien vers la fiche d'un jeu sur une boutique donnée. `storeId` identifie la boutique parmi
 * l'ensemble fixe de RAWG (1 = Steam) — voir `RawgMappers.extractSteamAppId`.
 */
@JsonClass(generateAdapter = true)
data class RawgStoreLinkDto(
    @Json(name = "id") val id: Long,
    @Json(name = "store_id") val storeId: Long,
    @Json(name = "url") val url: String? = null,
)
