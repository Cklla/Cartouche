package fr.cklla.cartouche.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.data.remote.igdb.IgdbApi
import fr.cklla.cartouche.data.remote.igdb.IgdbTokenProvider
import fr.cklla.cartouche.data.remote.igdb.IgdbTokenSource
import fr.cklla.cartouche.data.remote.igdb.TwitchAuthApi
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Fournit les clients Retrofit utilisés pour IGDB (catalogue, temps de jeu) et l'authentification
 * Twitch associée : deux base URL différentes, donc deux instances Retrofit séparées, qui
 * réutilisent l'`OkHttpClient`/`Moshi` déjà fournis par [NetworkModule] (pas d'authentification à
 * gérer côté OkHttp — voir [IgdbApi], les headers `Client-ID`/`Authorization` sont passés
 * explicitement par appel).
 */
@Module
@InstallIn(SingletonComponent::class)
object IgdbNetworkModule {

    @Provides
    @Singleton
    fun provideTwitchAuthApi(okHttpClient: OkHttpClient, moshi: Moshi): TwitchAuthApi = Retrofit.Builder()
        .baseUrl(TwitchAuthApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwitchAuthApi::class.java)

    @Provides
    fun provideIgdbTokenSource(provider: IgdbTokenProvider): IgdbTokenSource = provider

    @Provides
    @Singleton
    fun provideIgdbApi(okHttpClient: OkHttpClient, moshi: Moshi): IgdbApi = Retrofit.Builder()
        .baseUrl(IgdbApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(IgdbApi::class.java)
}
