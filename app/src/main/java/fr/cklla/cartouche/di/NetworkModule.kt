package fr.cklla.cartouche.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.BuildConfig
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** Fournit le client HTTP (OkHttp) et Moshi partagés par les clients Retrofit d'IGDB et de Twitch. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Les logs de requêtes ne sont utiles qu'en développement : BuildConfig.DEBUG évite de
        // bavarder inutilement en release. Niveau BODY pour pouvoir diagnostiquer les requêtes
        // IGDB (leurs réponses sont des données publiques de catalogue). Ce client ne sert PAS à
        // Twitch : la réponse de l'authentification contient le jeton d'accès, que le niveau BODY
        // écrirait en clair (voir `twitchHttpClient`).
        val level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        return OkHttpClient.Builder().addInterceptor(redactedLoggingInterceptor(level)).build()
    }
}

/**
 * Journal HTTP qui masque les secrets de l'en-tête et de l'URL : jeton d'accès (`Authorization`),
 * identifiant d'application (`Client-ID`) et, pour l'authentification Twitch, `client_id` et
 * `client_secret` passés en paramètres d'URL. `redactHeader` et `redactQueryParams` ne couvrent
 * **pas** le corps des réponses : un niveau `BODY` n'est donc sûr que pour des réponses sans secret.
 */
internal fun redactedLoggingInterceptor(
    level: HttpLoggingInterceptor.Level,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
    this.level = level
    redactHeader("Authorization")
    redactHeader("Client-ID")
    redactQueryParams("client_id", "client_secret")
}

/**
 * Client de l'authentification Twitch : même pool de connexions que [base], mais son journal est
 * remplacé par un niveau `HEADERS` au plus. La réponse de `oauth2/token` contient le jeton d'accès
 * IGDB (`access_token`) dans son **corps** : au niveau `BODY`, il serait écrit en clair dans logcat à
 * chaque renouvellement, sans que le masquage des en-têtes ni de l'URL n'y puisse rien.
 */
internal fun twitchHttpClient(
    base: OkHttpClient,
    debug: Boolean,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
): OkHttpClient {
    val level = if (debug) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
    return base.newBuilder().apply {
        interceptors().removeAll { it is HttpLoggingInterceptor }
        addInterceptor(redactedLoggingInterceptor(level, logger))
    }.build()
}
