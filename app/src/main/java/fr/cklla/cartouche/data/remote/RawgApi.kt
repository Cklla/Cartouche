package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgSearchResponseDto
import fr.cklla.cartouche.data.remote.dto.RawgStoreLinksResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API RAWG (https://rawg.io/apidocs) : l'endpoint de recherche de jeux (seul besoin pour la
 * recherche/fiche détail, le backlog lui-même n'est jamais stocké côté RAWG), plus l'endpoint des
 * liens boutiques, utilisé uniquement pour retrouver l'App ID Steam d'un jeu (voir
 * `IgdbPlaytimeRepository`, étape 1 de la correspondance IGDB).
 */
interface RawgApi {

    @GET("games")
    suspend fun searchGames(
        @Query("key") apiKey: String,
        @Query("search") query: String,
        @Query("page_size") pageSize: Int = 20,
    ): RawgSearchResponseDto

    @GET("games/{id}/stores")
    suspend fun getStoreLinks(
        @Path("id") rawgId: Long,
        @Query("key") apiKey: String,
    ): RawgStoreLinksResponseDto

    companion object {
        const val BASE_URL = "https://api.rawg.io/api/"
    }
}
