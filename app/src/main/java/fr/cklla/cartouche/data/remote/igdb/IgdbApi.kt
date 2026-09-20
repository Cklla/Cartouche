package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbExternalGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * API IGDB (https://api-docs.igdb.com), utilisée pour compléter deux informations d'un jeu déjà
 * ajouté au backlog : le temps de jeu estimé (`Game.estimatedPlaytime*Hours` : rapide/normal/
 * complet) et une liste de plateformes plus fiable que RAWG (`Game.platform`, voir
 * `IgdbGameMatch`) — RAWG reste la seule source pour tout le reste (recherche, ajout au backlog,
 * jaquette). Contrairement à RAWG, IGDB attend le corps de chaque requête au format "Apicalypse"
 * (texte brut, pas de JSON) : voir `IgdbMappers` pour la construction de ces requêtes.
 */
interface IgdbApi {

    /**
     * Endpoint `games`, utilisé à la fois pour la recherche par titre (`IgdbMappers.buildSearchQuery`)
     * et pour la requête de suivi par id (`IgdbMappers.buildPlatformsQuery`) — même endpoint,
     * requête Apicalypse différente selon le besoin de l'appelant.
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

    /** Recherche par identifiant externe (ex. App ID Steam) — voir `IgdbMappers.buildSteamExternalGameQuery`. */
    @Headers("Content-Type: text/plain")
    @POST("external_games")
    suspend fun findExternalGame(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") authorization: String,
        @Body query: RequestBody,
    ): List<IgdbExternalGameDto>

    companion object {
        const val BASE_URL = "https://api.igdb.com/v4/"
    }
}
