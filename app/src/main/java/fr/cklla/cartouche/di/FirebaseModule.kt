package fr.cklla.cartouche.di

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Fournit l'instance Firestore utilisée par l'app (l'authentification, elle, passe désormais par
 * Supabase : voir `SupabaseModule`).
 *
 * `FirebaseFirestore.getInstance()` lit sa configuration (projet, clé API...) dans
 * `google-services.json` via le plugin Gradle `google-services` — rien à paramétrer manuellement
 * ici, contrairement à Retrofit/RAWG.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // Volontairement pas @Singleton : terminer une instance Firestore (purge du cache local à la
    // déconnexion) la rend inutilisable pour toujours, il faut donc pouvoir en redemander une
    // neuve. `getInstance()` s'en charge et met lui-même en cache l'instance courante, on ne crée
    // donc pas un client par injection.
    @Provides
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
