package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.data.remote.dto.RawgGenreDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformWrapperDto
import fr.cklla.cartouche.domain.model.GameSearchResult

/**
 * Conversions entre les DTO RAWG et le modèle métier [GameSearchResult].
 *
 * Isolées dans des fonctions pures (hors Repository) pour rester testables
 * sans appel réseau ni serveur simulé.
 */

fun RawgGameDto.toDomain(): GameSearchResult = GameSearchResult(
    rawgId = id,
    title = name,
    platform = formatPlatforms(platforms),
    genre = firstGenre(genres),
    year = extractYear(released),
    coverUrl = backgroundImage,
)

/** RAWG renvoie une date complète ("2023-05-12") ou `null` pour un jeu sans date connue. */
fun extractYear(released: String?): String = released?.take(4) ?: ""

/** Les plateformes d'un jeu RAWG sont jointes en une seule chaîne d'affichage ("Switch/PC"). */
fun formatPlatforms(platforms: List<RawgPlatformWrapperDto>?): String =
    platforms.orEmpty().joinToString("/") { it.platform.name }

/** Un seul genre est affiché dans l'app (voir [GameSearchResult]) : le premier renvoyé par RAWG. */
fun firstGenre(genres: List<RawgGenreDto>?): String = genres.orEmpty().firstOrNull()?.name ?: ""
