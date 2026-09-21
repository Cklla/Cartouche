package fr.cklla.cartouche.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste, par année, si la notification "récap annuel" a déjà été envoyée — évite de renotifier
 * chaque jour tant que la fenêtre du récap reste ouverte (25-31 décembre ou tout janvier, voir
 * `recapTargetYear`). Un simple booléen par année ne justifie pas DataStore : `SharedPreferences`
 * en `MODE_PRIVATE`, même mécanisme que `IgdbTokenProvider` pour le token IGDB.
 */
@Singleton
class RecapNotificationPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasNotified(year: Int): Boolean =
        prefs.getStringSet(KEY_NOTIFIED_YEARS, emptySet())?.contains(year.toString()) == true

    fun markNotified(year: Int) {
        val updated = prefs.getStringSet(KEY_NOTIFIED_YEARS, emptySet()).orEmpty() + year.toString()
        prefs.edit { putStringSet(KEY_NOTIFIED_YEARS, updated) }
    }

    private companion object {
        const val PREFS_NAME = "recap_notification"
        const val KEY_NOTIFIED_YEARS = "notified_years"
    }
}
