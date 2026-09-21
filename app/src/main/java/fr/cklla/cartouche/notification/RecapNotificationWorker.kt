package fr.cklla.cartouche.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.cklla.cartouche.MainActivity
import fr.cklla.cartouche.R
import fr.cklla.cartouche.ui.stats.recapYearToNotify
import java.time.LocalDate

/**
 * Job périodique (une fois par jour, voir `CartoucheApplication`) qui envoie la notification locale
 * du récap annuel — condition purement basée sur la date de l'appareil ([recapYearToNotify]), sans
 * lien avec Firebase Cloud Messaging ni aucun événement serveur.
 *
 * `@HiltWorker`/`@AssistedInject` : seul moyen d'injecter des dépendances (ici [RecapNotificationPrefs])
 * dans un `Worker`, WorkManager instanciant lui-même la classe via son propre `WorkerFactory`
 * (`HiltWorkerFactory`, branché dans `CartoucheApplication.workManagerConfiguration`).
 */
@HiltWorker
class RecapNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: RecapNotificationPrefs,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val year = recapYearToNotify(LocalDate.now(), prefs::hasNotified) ?: return Result.success()
        showNotification(year)
        prefs.markNotified(year)
        return Result.success()
    }

    /**
     * Ne notifie jamais sans la permission `POST_NOTIFICATIONS` (Android 13+) : la carte du récap
     * dans Stats fonctionne déjà sans elle, la notification n'est jamais un prérequis pour la voir.
     */
    private fun showNotification(year: Int) {
        val context = applicationContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RECAP_NOTIFICATION_YEAR, year)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            year,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, RECAP_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.recap_notification_title, year))
            .setContentText(context.getString(R.string.recap_notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(year, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "recap_notification_daily_check"
    }
}
