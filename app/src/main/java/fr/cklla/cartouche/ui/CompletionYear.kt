package fr.cklla.cartouche.ui

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Année de complétion d'un jeu (fuseau horaire de l'appareil), déduite de [Game.completedAt] —
 * distincte de [Game.releaseYear], l'année de sortie du jeu. `null` tant que le jeu n'est jamais
 * passé par le statut [GameStatus.TERMINE].
 *
 * Partagé entre l'écran Bibliothèque (filtre "Terminé" par année) et l'écran Stats (mêmes années
 * disponibles pour les deux filtres, un seul point de calcul).
 */
fun completedYear(game: Game): Int? =
    game.completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }

/** Années disponibles pour le filtre "Terminé", triées de la plus récente à la plus ancienne. */
fun availableCompletedYears(games: List<Game>): List<Int> =
    games.filter { it.status == GameStatus.TERMINE }
        .mapNotNull(::completedYear)
        .distinct()
        .sortedDescending()
