package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API RAWG (https://rawg.io/apidocs) : uniquement l'endpoint de recherche de jeux,
 * seul besoin de l'app (le backlog lui-même n'est jamais stocké côté RAWG).
 */
interface RawgApi {

    @GET("games")
    suspend fun searchGames(
        @Query("key") apiKey: String,
        @Query("search") query: String,
        @Query("page_size") pageSize: Int = 20,
    ): RawgSearchResponseDto

    companion object {
        const val BASE_URL = "https://api.rawg.io/api/"
    }
}
