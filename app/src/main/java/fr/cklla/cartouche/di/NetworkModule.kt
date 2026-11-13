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
        // IGDB. Ce niveau journalise aussi les **en-têtes** (jeton d'accès `Authorization`,
        // identifiant d'application `Client-ID`) et l'URL, où l'authentification Twitch passe
        // `client_id` et `client_secret` en paramètres : tout cela est masqué.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Client-ID")
            redactQueryParams("client_id", "client_secret")
        }
        return OkHttpClient.Builder().addInterceptor(logging).build()
    }
}
