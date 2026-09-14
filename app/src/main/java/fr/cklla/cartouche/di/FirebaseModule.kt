package fr.cklla.cartouche.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit les instances Firebase, uniques pour toute la durée de vie de l'application.
 *
 * `FirebaseAuth.getInstance()`/`FirebaseFirestore.getInstance()` lisent leur configuration
 * (projet, clé API...) dans `google-services.json` via le plugin Gradle `google-services` —
 * rien à paramétrer manuellement ici, contrairement à Retrofit/RAWG.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
