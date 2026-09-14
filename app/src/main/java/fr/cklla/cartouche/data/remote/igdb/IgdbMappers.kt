package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import java.util.Locale

/**
 * Fonctions pures autour d'IGDB (construction de requêtes "Apicalypse", correspondance de titre) :
 * isolées du repository pour rester testables sans appel réseau, même principe que
 * `RawgMappers`/`SearchFiltering` ailleurs dans le projet.
 */

/** Requête de recherche par titre, jusqu'à 10 candidats (id + nom seulement, c'est tout ce qu'il faut pour le matching). */
fun buildSearchQuery(title: String): String = "search \"${escapeQueryText(title)}\"; fields id,name; limit 10;"

/** Requête de durée de vie pour un jeu IGDB déjà identifié. */
fun buildTimeToBeatQuery(igdbGameId: Long): String =
    "fields hastily,normally,completely; where game_id = $igdbGameId; limit 1;"

private fun escapeQueryText(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * Il n'y a pas de correspondance directe entre les ids RAWG et IGDB : on doit retrouver le jeu
 * par similarité de titre parmi les candidats renvoyés par la recherche IGDB. Renvoie `null` si
 * aucun candidat n'est jugé assez proche plutôt que de forcer une correspondance hasardeuse (le
 * temps de jeu resterait alors "non disponible", voir `IgdbPlaytimeRepositoryImpl`).
 */
fun findBestMatch(title: String, candidates: List<IgdbGameDto>): IgdbGameDto? {
    val normalizedTitle = normalizeForMatching(title)

    // Correspondance exacte (à la casse/ponctuation près) : cas de loin le plus fréquent, pas
    // besoin de calculer une distance pour ça.
    candidates.firstOrNull { normalizeForMatching(it.name) == normalizedTitle }?.let { return it }

    return candidates
        .map { it to titleSimilarity(normalizedTitle, normalizeForMatching(it.name)) }
        .maxByOrNull { (_, similarity) -> similarity }
        ?.takeIf { (_, similarity) -> similarity >= MATCH_SIMILARITY_THRESHOLD }
        ?.first
}

/** En-dessous de ce seuil (proportion de caractères en commun), le candidat est jugé peu fiable. */
private const val MATCH_SIMILARITY_THRESHOLD = 0.75

private fun normalizeForMatching(value: String): String =
    value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == ' ' }.trim()

/** Similarité de deux chaînes déjà normalisées, entre 0 (rien en commun) et 1 (identiques). */
private fun titleSimilarity(a: String, b: String): Double {
    val maxLength = maxOf(a.length, b.length)
    if (maxLength == 0) return 1.0
    return 1.0 - levenshteinDistance(a, b).toDouble() / maxLength
}

private fun levenshteinDistance(a: String, b: String): Int {
    val distances = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) distances[i][0] = i
    for (j in 0..b.length) distances[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            distances[i][j] = if (a[i - 1] == b[j - 1]) {
                distances[i - 1][j - 1]
            } else {
                1 + minOf(distances[i - 1][j], distances[i][j - 1], distances[i - 1][j - 1])
            }
        }
    }
    return distances[a.length][b.length]
}

/** IGDB exprime la durée de vie en secondes ; l'app l'affiche en heures entières. */
fun secondsToHours(seconds: Int): Int = seconds / 3600
