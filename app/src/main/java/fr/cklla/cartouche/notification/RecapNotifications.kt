package fr.cklla.cartouche.notification

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import fr.cklla.cartouche.R

/** Identifiant du canal de notification dédié au récap annuel (voir RecapNotificationWorker). */
const val RECAP_NOTIFICATION_CHANNEL_ID = "recap_annuel"

/** Nom de l'extra d'`Intent` transportant l'année à ouvrir quand on tape la notification (voir MainActivity). */
const val EXTRA_RECAP_NOTIFICATION_YEAR = "recap_notification_year"

/**
 * Crée le canal de notification "récap annuel", idempotent (`createNotificationChannel` recrée le
 * même canal sans effet s'il existe déjà) — appelé au démarrage de l'app plutôt qu'au premier envoi,
 * car les canaux doivent exister avant que l'utilisateur puisse les configurer dans les paramètres
 * système. No-op sous Android 8 (pas de notion de canal avant `O`, géré par NotificationChannelCompat).
 */
fun createRecapNotificationChannel(context: Context) {
    val channel = NotificationChannelCompat.Builder(RECAP_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName(context.getString(R.string.recap_notification_channel_name))
        .setDescription(context.getString(R.string.recap_notification_channel_description))
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}
