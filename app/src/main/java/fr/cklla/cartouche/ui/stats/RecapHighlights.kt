package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.bibliotheque.filterGames
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Logique pure du récap en images : sélection des coups de cœur et des plus longues parties, dates
 * posées à la main, faits de l'année et liste des slides. Rien ici ne dépend d'Android ni de
 * l'horloge : le fuseau et la langue sont des paramètres.
 *
 * Les jeux de l'année viennent de [filterGames] (statut Terminé et année de fin, donc
 * `completedYear`) et les totaux de [computeStats] : les chiffres coïncident toujours avec la
 * Bibliothèque et avec `RecapScreen`, sans calcul parallèle.
 *
 * Limite connue : `completedYear` lit le fuseau de l'appareil, tandis que les jours et mois des
 * faits lisent le `zone` reçu. Identiques en production (le ViewModel passe le fuseau de
 * l'appareil) ; les tests de fuseau alignent les deux.
 */

/** Plafond de coups de cœur : au-delà, on garde les jeux 5 étoiles terminés le plus récemment. */
const val MAX_FAVORITES = 10

/** Nombre minimal de coups de cœur visé : en dessous, on complète avec des jeux notés 4. */
const val MIN_FAVORITES = 3

/** Jusqu'à ce nombre de coups de cœur, la grille passe à 2 colonnes pour garder des jaquettes lisibles. */
const val FAVORITES_TWO_COLUMNS_MAX = 4

/** Nombre de parties dans le classement des plus longues. */
const val MAX_LONGEST = 5

/** En dessous de ce nombre de parties avec une durée, le classement n'a pas de sens : slide omise. */
const val MIN_LONGEST = 2

/** Colonnes de la mosaïque finale. */
const val MOSAIC_COLUMNS = 5

/** Colonnes de la grille des coups de cœur selon leur nombre. */
fun favoritesGridColumns(count: Int): Int = if (count <= FAVORITES_TWO_COLUMNS_MAX) 2 else 3

/** Jeux au statut Terminé dont l'année de fin est [year]. */
private fun completedIn(games: List<Game>, year: Int): List<Game> =
    filterGames(games, BacklogFilter.TERMINE, year)

/** Du plus récemment terminé au plus ancien ; égalité : titre puis id, pour un ordre déterministe. */
private val mostRecentlyCompletedFirst: Comparator<Game> =
    compareByDescending<Game> { it.completedAt ?: Long.MIN_VALUE }.thenBy { it.title }.thenBy { it.id }

/** Du plus ancien au plus récent ; égalité : titre puis id. */
private val chronological: Comparator<Game> =
    compareBy<Game> { it.completedAt ?: Long.MAX_VALUE }.thenBy { it.title }.thenBy { it.id }

/**
 * Coups de cœur de l'année [year]. Seuls comptent les jeux Terminé dont l'année de fin est [year] ;
 * les notes `null` sont ignorées.
 *
 * - tous les jeux notés 5, du plus récemment terminé au plus ancien, plafonnés à [MAX_FAVORITES] ;
 * - s'il y en a moins de [MIN_FAVORITES], complétés par des jeux notés 4 (même ordre) jusqu'à en
 *   avoir [MIN_FAVORITES], les 5 étoiles restant devant ;
 * - liste vide si aucun jeu n'est noté 4 ou 5.
 */
fun selectFavorites(games: List<Game>, year: Int): List<Game> {
    val rated = completedIn(games, year).filter { it.rating != null }
    val fives = rated.filter { it.rating == 5 }.sortedWith(mostRecentlyCompletedFirst).take(MAX_FAVORITES)
    if (fives.size >= MIN_FAVORITES) return fives
    val fours = rated.filter { it.rating == 4 }.sortedWith(mostRecentlyCompletedFirst)
    return fives + fours.take(MIN_FAVORITES - fives.size)
}

/**
 * Les [MAX_LONGEST] plus longues parties de l'année : jeux Terminé de [year] dont le temps de jeu
 * est strictement positif, classés par durée décroissante (égalité : terminé le plus récemment, puis
 * titre). Liste vide s'il y a moins de [MIN_LONGEST] jeux avec une durée.
 */
fun selectLongest(games: List<Game>, year: Int): List<Game> {
    val withDuration = completedIn(games, year).filter { it.userPlaytimeHours > 0 }
    if (withDuration.size < MIN_LONGEST) return emptyList()
    return withDuration
        .sortedWith(compareByDescending<Game> { it.userPlaytimeHours }.thenComparing(mostRecentlyCompletedFirst))
        .take(MAX_LONGEST)
}

/**
 * Vrai si [timestamp] est exactement le 1er juillet à 12:00:00.000 dans [zone] : l'horodatage posé
 * quand l'année de fin est choisie à la main depuis la fiche (voir `timestampForYear`), donc sans
 * jour ni mois réels.
 */
fun isApproximateTimestamp(timestamp: Long, zone: ZoneId): Boolean {
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    return dateTime.month == Month.JULY &&
        dateTime.dayOfMonth == 1 &&
        dateTime.toLocalTime() == LocalTime.NOON
}

/** Un jeu et le jour où il a été terminé. */
data class DatedGame(val game: Game, val date: LocalDate)

/** Mois le plus chargé de l'année et le nombre de jeux terminés ce mois-là. */
data class BusiestMonth(val month: Month, val count: Int)

/**
 * Faits de l'année ; chacun vaut `null` quand la donnée n'existe pas.
 *
 * @param first premier jeu terminé de l'année (date exacte uniquement).
 * @param last dernier jeu terminé ; `null` s'il n'y a qu'un jeu à date exacte, pour ne pas présenter
 *   le même jeu comme premier et dernier.
 * @param busiestMonth mois qui compte le plus de jeux terminés (date exacte uniquement).
 * @param oldest jeu terminé dont l'année de sortie est la plus ancienne (dates approximatives
 *   incluses, car il ne dépend que de l'année de sortie).
 */
data class RecapFacts(
    val first: DatedGame? = null,
    val last: DatedGame? = null,
    val busiestMonth: BusiestMonth? = null,
    val oldest: Game? = null,
) {
    val isEmpty: Boolean get() = first == null && last == null && busiestMonth == null && oldest == null
}

/**
 * Faits de l'année [year].
 *
 * - Les jeux dont la date a été posée à la main ([isApproximateTimestamp]) sont exclus du premier /
 *   dernier et du mois le plus chargé : leur jour est inventé.
 * - Mois le plus chargé : celui qui compte le plus de jeux ; égalité : le plus récent.
 * - Jeu le plus ancien : année de sortie minimale ; égalité : le terminé le plus récemment. Les jeux
 *   sans année de sortie sont ignorés.
 */
fun computeRecapFacts(games: List<Game>, year: Int, zone: ZoneId): RecapFacts {
    val completed = completedIn(games, year)
    val exact = completed
        .filter { it.completedAt != null && !isApproximateTimestamp(it.completedAt, zone) }
        .sortedWith(chronological)

    val first = exact.firstOrNull()?.datedIn(zone)
    val last = if (exact.size >= 2) exact.last().datedIn(zone) else null

    val busiestMonth = exact
        .groupingBy { Instant.ofEpochMilli(checkNotNull(it.completedAt)).atZone(zone).month }
        .eachCount()
        .entries
        .maxWithOrNull(compareBy<Map.Entry<Month, Int>> { it.value }.thenBy { it.key.value })
        ?.let { BusiestMonth(it.key, it.value) }

    val oldest = completed
        .filter { it.releaseYear != null }
        .sortedWith(compareBy<Game> { it.releaseYear }.thenComparing(mostRecentlyCompletedFirst))
        .firstOrNull()

    return RecapFacts(first = first, last = last, busiestMonth = busiestMonth, oldest = oldest)
}

private fun Game.datedIn(zone: ZoneId): DatedGame =
    DatedGame(this, Instant.ofEpochMilli(checkNotNull(completedAt)).atZone(zone).toLocalDate())

/** Tous les jeux terminés de l'année, du plus ancien au plus récent (égalité : titre, puis id). */
fun mosaicGames(games: List<Game>, year: Int): List<Game> =
    completedIn(games, year).sortedWith(chronological)

/** Une plateforme et le nombre de jeux terminés dessus dans l'année. */
data class PlatformCount(val platform: String, val count: Int)

/** Plateformes triées par nombre de jeux décroissant, puis par nom. */
fun sortedPlatformCounts(countsByPlatform: Map<String, Int>): List<PlatformCount> =
    countsByPlatform.map { (platform, count) -> PlatformCount(platform, count) }
        .sortedWith(compareByDescending<PlatformCount> { it.count }.thenBy { it.platform })

/** Une slide du récap en images. [key] est stable : il identifie la page du pager entre deux recompositions. */
sealed interface RecapSlide {
    val key: String

    /** Total de l'année : jeux terminés, heures jouées sur ces jeux et jeux abandonnés. */
    data class Total(
        val year: Int,
        val completedCount: Int,
        val totalHours: Int,
        val abandonedCount: Int,
    ) : RecapSlide {
        override val key: String = "total"
    }

    data class Favorites(val games: List<Game>) : RecapSlide {
        override val key: String = "favorites"
        val columns: Int get() = favoritesGridColumns(games.size)
    }

    data class Longest(val games: List<Game>) : RecapSlide {
        override val key: String = "longest"
    }

    data class Platforms(val counts: List<PlatformCount>) : RecapSlide {
        override val key: String = "platforms"
        val maxCount: Int get() = counts.maxOfOrNull { it.count } ?: 0
    }

    data class Facts(val facts: RecapFacts) : RecapSlide {
        override val key: String = "facts"
    }

    /** Mosaïque finale : [games] est dans l'ordre chronologique des fins. */
    data class Mosaic(val games: List<Game>) : RecapSlide {
        override val key: String = "mosaic"
    }
}

/**
 * Slides du récap de [year], dans l'ordre : total, coups de cœur, plus longues parties, plateformes,
 * faits, mosaïque. Une slide sans rien à montrer est omise ; sans aucun jeu terminé ni abandonné
 * dans l'année, la liste est vide. Les jeux abandonnés ne comptent que dans la slide Total.
 */
fun buildRecapSlides(games: List<Game>, year: Int, zone: ZoneId): List<RecapSlide> {
    val stats = computeStats(games, selectedYear = year)
    val abandonedCount = stats.countsByStatus[GameStatus.ABANDONNE] ?: 0
    if (stats.completedCount == 0 && abandonedCount == 0) return emptyList()

    val favorites = selectFavorites(games, year)
    val longest = selectLongest(games, year)
    val platforms = sortedPlatformCounts(stats.completedByPlatform)
    val facts = computeRecapFacts(games, year, zone)
    val mosaic = mosaicGames(games, year)
    return buildList {
        add(RecapSlide.Total(year, stats.completedCount, stats.totalHoursPlayed, abandonedCount))
        if (favorites.isNotEmpty()) add(RecapSlide.Favorites(favorites))
        if (longest.isNotEmpty()) add(RecapSlide.Longest(longest))
        if (platforms.isNotEmpty()) add(RecapSlide.Platforms(platforms))
        if (!facts.isEmpty) add(RecapSlide.Facts(facts))
        if (mosaic.isNotEmpty()) add(RecapSlide.Mosaic(mosaic))
    }
}

/**
 * « 12 janvier », avec « 1er » pour le premier du mois quand [locale] est le français. La langue est
 * un paramètre : l'appelant (l'UI) décide, la logique n'en fige aucune.
 */
fun formatRecapDate(date: LocalDate, locale: Locale): String =
    if (date.dayOfMonth == 1 && locale.language == "fr") {
        "1er ${monthName(date.month, locale)}"
    } else {
        date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    }

/** Nom complet du mois dans [locale], en minuscules en français (« janvier »). */
fun monthName(month: Month, locale: Locale): String = month.getDisplayName(TextStyle.FULL, locale)
