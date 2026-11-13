package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * API IGDB (https://api-docs.igdb.com), source unique du catalogue de jeux : recherche, ajout au
 * backlog, jaquette, année, genre, plateformes et temps de jeu estimé (`Game.estimatedPlaytime*`).
 * IGDB attend le corps de chaque requête au format "Apicalypse" (texte brut, pas de JSON) : voir
 * `IgdbMappers` pour la construction de ces requêtes et `IgdbGateway` pour leur enchaînement.
 */
interface IgdbApi {

    /**
     * Endpoint `games`, utilisé pour la recherche par texte, la lecture par id et la recherche des
     * éditions et portages d'un jeu — même endpoint, requête Apicalypse différente selon le besoin
     * de l'appelant (voir `IgdbMappers`).
     */
    @Headers("Content-Type: text/plain")
    @POST("games")
    suspend fun searchGames(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") authorization: String,
        @Body query: RequestBody,
    ): List<IgdbGameDto>

    @Headers("Content-Type: text/plain")
    @POST("game_time_to_beats")
    suspend fun getTimeToBeat(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") authorization: String,
        @Body query: RequestBody,
    ): List<IgdbTimeToBeatDto>

    companion object {
        const val BASE_URL = "https://api.igdb.com/v4/"
    }
}
