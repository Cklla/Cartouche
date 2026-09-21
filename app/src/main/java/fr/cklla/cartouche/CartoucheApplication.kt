package fr.cklla.cartouche

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import fr.cklla.cartouche.notification.RecapNotificationWorker
import fr.cklla.cartouche.notification.createRecapNotificationChannel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Point d'entrée de l'injection de dépendances Hilt : cette classe déclenche
 * la génération du graphe de dépendances au démarrage de l'application.
 *
 * Implémente `Configuration.Provider` pour que WorkManager (initialisé à la demande, voir sa
 * documentation) utilise `HiltWorkerFactory` — seul moyen pour `RecapNotificationWorker` de se
 * faire injecter ses dépendances comme n'importe quel autre composant Hilt de l'app.
 */
@HiltAndroidApp
class CartoucheApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // App Check doit être installé avant le premier appel à Firestore ou à Auth, sans quoi
        // les requêtes partiraient sans jeton d'attestation. `installAppCheck` a une implémentation
        // par type de build (voir src/debug et src/release) : Play Integrity en release, jeton de
        // debug sinon.
        installAppCheck(this)

        createRecapNotificationChannel(this)
        // Vérifie une fois par jour si le récap annuel vient de devenir visible (voir
        // RecapNotificationWorker) — largement suffisant, la fenêtre dure plusieurs jours à chaque
        // fois. `KEEP` : un redémarrage de l'app ne doit pas relancer le cycle du job déjà planifié.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecapNotificationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecapNotificationWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
