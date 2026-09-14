package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.TwitchTokenResponseDto
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Flow OAuth2 "client credentials" de Twitch (https://dev.twitch.tv), seul moyen d'obtenir un
 * token pour appeler l'API IGDB (propriété Twitch). Aucun compte utilisateur impliqué : le
 * `client_id`/`client_secret` identifient l'app elle-même, pas une personne.
 */
interface TwitchAuthApi {

    @POST("oauth2/token")
    suspend fun getAppAccessToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("grant_type") grantType: String = "client_credentials",
    ): TwitchTokenResponseDto

    companion object {
        const val BASE_URL = "https://id.twitch.tv/"
    }
}
