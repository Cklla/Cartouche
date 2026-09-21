package fr.cklla.cartouche.ui.stats

import java.time.LocalDate

/**
 * Année pour laquelle envoyer la notification "récap annuel" aujourd'hui, ou `null` si aucune
 * notification n'est due — soit la fenêtre du récap n'est pas ouverte aujourd'hui ([recapTargetYear],
 * même logique que la carte de l'écran Stats), soit elle l'est mais l'utilisateur a déjà été
 * notifié pour cette année précise ([alreadyNotified]).
 *
 * [alreadyNotified] est un prédicat plutôt qu'un `Set<Int>` pour rester découplé du mécanisme de
 * persistance (`RecapNotificationPrefs`, basé sur les `SharedPreferences`) : cette fonction reste
 * ainsi testable sans dépendance Android.
 */
fun recapYearToNotify(today: LocalDate, alreadyNotified: (Int) -> Boolean): Int? =
    recapTargetYear(today)?.takeUnless(alreadyNotified)
