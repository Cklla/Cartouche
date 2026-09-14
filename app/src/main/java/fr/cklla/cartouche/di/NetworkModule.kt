package fr.cklla.cartouche.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.RawgApi
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Fournit le client Retrofit/Moshi utilisé pour interroger l'API RAWG (recherche de jeux). */
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
        // bavarder inutilement en release. Niveau BODY (et non BASIC) pour pouvoir diagnostiquer
        // les correspondances IGDB (voir `IgdbPlaytimeRepositoryImpl`) : les clés/tokens passent
        // en en-têtes, jamais dans le corps des requêtes, donc pas de fuite malgré ce niveau.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder().addInterceptor(logging).build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(RawgApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideRawgApi(retrofit: Retrofit): RawgApi = retrofit.create(RawgApi::class.java)
}
