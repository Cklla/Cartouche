package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.data.remote.dto.RawgGenreDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformWrapperDto
import fr.cklla.cartouche.data.remote.dto.RawgStoreLinkDto
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

/** Identifiant de la boutique Steam dans l'ensemble fixe de boutiques RAWG. */
private const val RAWG_STEAM_STORE_ID = 1L

private val STEAM_APP_ID_REGEX = Regex("""store\.steampowered\.com/app/(\d+)""", RegexOption.IGNORE_CASE)

/**
 * Extrait l'App ID Steam numérique du lien boutique Steam d'un jeu RAWG (ex.
 * `https://store.steampowered.com/app/1145360/Hades/` → `1145360`), ou `null` si le jeu n'a pas
 * de fiche Steam sur RAWG. C'est le signal de correspondance IGDB le plus fiable (voir
 * `IgdbPlaytimeRepository`) : contrairement au nom, un App ID Steam ne peut désigner qu'un seul jeu.
 */
fun extractSteamAppId(storeLinks: List<RawgStoreLinkDto>): Long? = storeLinks
    .firstOrNull { it.storeId == RAWG_STEAM_STORE_ID }
    ?.url
    ?.let { STEAM_APP_ID_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() }
