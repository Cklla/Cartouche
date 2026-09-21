package fr.cklla.cartouche.ui.stats

import java.time.LocalDate

/**
 * Année ciblée par la carte "Récap" de l'écran Stats, selon la date du jour sur l'appareil
 * ([today] passé en paramètre plutôt que lu via `LocalDate.now()` ici, pour rester testable sur
 * chaque cas limite) — `null` si la carte ne doit pas s'afficher du tout.
 *
 * Deux fenêtres, calquées sur le moment où un bilan de l'année a du sens pour l'utilisateur :
 * - Du 25 au 31 décembre inclus : l'année en cours touche à sa fin, le récap porte sur elle.
 * - Du 1er au 31 janvier inclus : l'année vient de se terminer, le récap porte sur l'année
 *   précédente (celle qui vient de se terminer, pas l'année civile en cours).
 * En dehors de ces deux fenêtres, pas de récap pertinent à proposer.
 */
fun recapTargetYear(today: LocalDate): Int? = when {
    today.monthValue == 12 && today.dayOfMonth >= 25 -> today.year
    today.monthValue == 1 -> today.year - 1
    else -> null
}
