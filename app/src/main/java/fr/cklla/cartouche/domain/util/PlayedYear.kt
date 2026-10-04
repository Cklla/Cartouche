package fr.cklla.cartouche.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Année la plus ancienne proposée comme année de fin ou d'abandon. C'est à la fois la borne basse
 * quand l'année de sortie du jeu est inconnue et un plancher absolu : un horodatage antérieur à
 * 1970 serait négatif, et les règles Firestore bornent `completedAt` / `abandonedAt` à partir de 0.
 */
const val EARLIEST_PLAYED_YEAR = 1970

/**
 * Années que l'on peut attribuer comme année de fin (Terminé) ou d'abandon (Abandonné), de la plus
 * récente à la plus ancienne : de l'année de [nowMillis] jusqu'à l'année de sortie du jeu
 * ([EARLIEST_PLAYED_YEAR] si elle est inconnue ou antérieure). Un jeu pas encore sorti mais déjà
 * Terminé reste rattaché à l'année en cours : on ne propose jamais d'année future.
 *
 * [nowMillis] et [zone] sont passés en paramètres (plutôt que lus ici) pour que le calcul reste pur
 * et testable sans horloge.
 */
fun selectablePlayedYears(releaseYear: Int?, nowMillis: Long, zone: ZoneId): List<Int> {
    val currentYear = yearOf(nowMillis, zone)
    return (currentYear downTo earliestPlayedYear(releaseYear, currentYear)).toList()
}

/**
 * Horodatage (epoch millis) à enregistrer pour qu'un jeu soit classé dans l'année [year], ou `null`
 * si cette année n'est pas permise (future, ou antérieure à la sortie du jeu) : l'appelant ne doit
 * alors rien écrire.
 *
 * - si [currentTimestamp] tombe déjà dans [year], il est conservé tel quel (choisir l'année déjà
 *   enregistrée ne déplace pas la date) ;
 * - année en cours sinon : l'instant présent ([nowMillis]) ;
 * - année passée sinon : le 1er juillet à midi dans [zone]. Le milieu de l'année laisse 6 mois de
 *   marge de chaque côté, donc relire l'année de cet horodatage dans un fuseau quelconque (de
 *   UTC-12 à UTC+14) redonne toujours [year].
 */
fun timestampForYear(
    year: Int,
    currentTimestamp: Long?,
    releaseYear: Int?,
    nowMillis: Long,
    zone: ZoneId,
): Long? {
    val currentYear = yearOf(nowMillis, zone)
    if (year > currentYear || year < earliestPlayedYear(releaseYear, currentYear)) return null

    return when {
        currentTimestamp != null && yearOf(currentTimestamp, zone) == year -> currentTimestamp
        year == currentYear -> nowMillis
        else -> ZonedDateTime.of(year, 7, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    }
}

// Borne basse : l'année de sortie, jamais en dessous du plancher (voir [EARLIEST_PLAYED_YEAR]) et
// jamais au-dessus de l'année en cours (jeu annoncé pour plus tard mais déjà marqué Terminé).
private fun earliestPlayedYear(releaseYear: Int?, currentYear: Int): Int =
    minOf(maxOf(releaseYear ?: EARLIEST_PLAYED_YEAR, EARLIEST_PLAYED_YEAR), currentYear)

private fun yearOf(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).year
