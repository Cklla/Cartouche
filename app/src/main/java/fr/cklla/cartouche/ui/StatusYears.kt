package fr.cklla.cartouche.ui

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Dérivation de l'année à laquelle un jeu est passé par un statut daté (Terminé ou Abandonné),
 * fuseau horaire de l'appareil — distincte de [Game.releaseYear], l'année de sortie du jeu.
 *
 * Partagé entre l'écran Bibliothèque (filtre par année sous "Terminé" et "Abandonné") et l'écran
 * Stats (mêmes années disponibles, un seul point de calcul).
 */

/** `null` tant que le jeu n'est jamais passé par le statut [GameStatus.TERMINE]. */
fun completedYear(game: Game): Int? =
    game.completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }

/** Années disponibles pour le filtre "Terminé", triées de la plus récente à la plus ancienne. */
fun availableCompletedYears(games: List<Game>): List<Int> =
    games.filter { it.status == GameStatus.TERMINE }
        .mapNotNull(::completedYear)
        .distinct()
        .sortedDescending()

/** `null` tant que le jeu n'est jamais passé par le statut [GameStatus.ABANDONNE]. */
fun abandonedYear(game: Game): Int? =
    game.abandonedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }

/** Années disponibles pour le filtre "Abandonné", triées de la plus récente à la plus ancienne. */
fun availableAbandonedYears(games: List<Game>): List<Int> =
    games.filter { it.status == GameStatus.ABANDONNE }
        .mapNotNull(::abandonedYear)
        .distinct()
        .sortedDescending()

/**
 * Union des années de complétion et d'abandon, utilisée par Stats : contrairement à la
 * Bibliothèque (un onglet de statut à la fois), Stats affiche les deux compteurs simultanément
 * pour l'année sélectionnée, donc une année n'ayant que des abandons doit rester sélectionnable.
 */
fun availableActivityYears(games: List<Game>): List<Int> =
    (availableCompletedYears(games) + availableAbandonedYears(games)).distinct().sortedDescending()
