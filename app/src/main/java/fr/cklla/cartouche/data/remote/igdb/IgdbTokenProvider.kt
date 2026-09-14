package fr.cklla.cartouche.data.remote.igdb

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.cartouche.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fournit un token d'accès Twitch valide pour appeler l'API IGDB, en le rafraîchissant
 * automatiquement à l'expiration seulement — jamais à chaque appel (le token
 * "client credentials" de Twitch est valable environ 60 jours).
 *
 * Le token est mis en cache dans les `SharedPreferences` (pas seulement en mémoire) : sans ça,
 * chaque redémarrage de l'app redemanderait un nouveau token à Twitch alors que l'ancien est
 * probablement encore valide pendant des semaines.
 */
@Singleton
class IgdbTokenProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val twitchAuthApi: TwitchAuthApi,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Évite deux rafraîchissements concurrents (ex. deux fiches Détail ouvertes coup sur coup
    // avant qu'un premier token ne soit obtenu) qui demanderaient chacun un nouveau token à Twitch.
    private val mutex = Mutex()

    /**
     * Renvoie un token valide, ou `null` si son obtention échoue (identifiants absents ou
     * invalides, pas de réseau, quota Twitch...). L'appelant traite alors le temps de jeu IGDB
     * comme "non disponible", jamais comme une erreur bloquante.
     */
    suspend fun getValidToken(): String? = mutex.withLock {
        val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT_MILLIS, 0L)
        if (cachedToken != null && System.currentTimeMillis() < expiresAtMillis - REFRESH_MARGIN_MILLIS) {
            return@withLock cachedToken
        }
        refreshToken()
    }

    private suspend fun refreshToken(): String? {
        if (BuildConfig.IGDB_CLIENT_ID.isBlank() || BuildConfig.IGDB_CLIENT_SECRET.isBlank()) return null

        return runCatching {
            twitchAuthApi.getAppAccessToken(
                clientId = BuildConfig.IGDB_CLIENT_ID,
                clientSecret = BuildConfig.IGDB_CLIENT_SECRET,
            )
        }.getOrNull()?.let { response ->
            prefs.edit {
                putString(KEY_ACCESS_TOKEN, response.accessToken)
                putLong(KEY_EXPIRES_AT_MILLIS, System.currentTimeMillis() + response.expiresInSeconds * 1_000)
            }
            response.accessToken
        }
    }

    private companion object {
        const val PREFS_NAME = "igdb_auth"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_EXPIRES_AT_MILLIS = "expires_at_millis"

        /** Rafraîchit un peu avant l'expiration réelle, pour ne jamais tenter un appel avec un token tout juste périmé. */
        const val REFRESH_MARGIN_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
