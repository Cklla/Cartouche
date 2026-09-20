package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbPlatformDto
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Fonctions pures autour d'IGDB (construction de requêtes "Apicalypse", correspondance de titre) :
 * isolées du repository pour rester testables sans appel réseau, même principe que
 * `RawgMappers`/`SearchFiltering` ailleurs dans le projet.
 *
 * La correspondance RAWG → IGDB se fait en cascade, du signal le plus fiable au moins fiable (voir
 * `IgdbPlaytimeRepositoryImpl`) :
 * 1. App ID Steam (`external_games`, [buildSteamExternalGameQuery]) — fiable à 100%, saute les
 *    étapes suivantes si trouvé.
 * 2. Nom normalisé + année de sortie ([findBestMatch]) — utilisé seulement si l'étape 1 n'a rien
 *    donné (pas de fiche Steam RAWG, ou pas de correspondance côté IGDB).
 * 3. Aucun match fiable → "non disponible".
 *
 * Une fois l'id IGDB résolu (quelle que soit la branche), il sert à la fois à retrouver le temps de
 * jeu estimé (`game_time_to_beats`) et, depuis l'ajout de [formatIgdbPlatforms], à fiabiliser
 * `Game.platform` (utile notamment pour distinguer Switch/Switch 2, que RAWG ne différencie pas) —
 * RAWG reste la seule source pour la recherche, l'ajout au backlog et les jaquettes.
 */

/**
 * Requête de recherche par titre, jusqu'à 30 candidats. `first_release_date` est demandé en plus
 * de l'id/nom : c'est le signal utilisé par [findBestMatch] pour départager des candidats au nom
 * proche (ex. plusieurs éditions/remakes d'un même jeu). `platforms.name` est demandé dans ce même
 * appel plutôt que dans un second : une fois le meilleur candidat retenu par [findBestMatch], sa
 * liste de plateformes IGDB est déjà disponible sans requête supplémentaire (voir
 * `IgdbPlaytimeRepositoryImpl`, branche nom+année de la cascade).
 *
 * Pas de filtre sur le champ `category` d'IGDB (DLC/bundle/mod...) : essayé, mais rejeté — de
 * nombreux jeux de base n'ont tout simplement pas ce champ renseigné côté IGDB (ex. *Persona 3
 * Reload* lui-même), et une comparaison IGDB sur un champ absent ne matche jamais, filtre par
 * inclusion ou par exclusion. La limite est donc simplement plus généreuse (30 plutôt que 10) pour
 * que le jeu de base ne soit pas noyé hors de la fenêtre par du contenu additionnel au nom proche
 * (ex. les nombreux packs de costumes/musique de *Persona 3 Reload*, qui partagent le même
 * préfixe) : [findBestMatch] élimine ensuite ces candidats sur la seule similarité de nom, sans
 * avoir besoin de connaître leur catégorie.
 */
fun buildSearchQuery(title: String): String =
    "search \"${escapeQueryText(title)}\"; fields id,name,first_release_date,platforms.name; limit 30;"

/** Requête de durée de vie pour un jeu IGDB déjà identifié. */
fun buildTimeToBeatQuery(igdbGameId: Long): String =
    "fields hastily,normally,completely; where game_id = $igdbGameId; limit 1;"

/**
 * Requête `external_games` retrouvant le jeu IGDB associé à un App ID Steam. `category = 1` est
 * le code IGDB pour la boutique Steam (constante documentée par l'API, pas de nom symbolique côté
 * IGDB v4).
 */
fun buildSteamExternalGameQuery(steamAppId: Long): String =
    "fields game; where uid = \"$steamAppId\" & category = 1; limit 1;"

/**
 * Requête de suivi minimale pour récupérer les plateformes d'un jeu IGDB déjà identifié par App ID
 * Steam (`external_games` ne renvoie que l'id, voir [buildSteamExternalGameQuery]) — contrairement
 * à la branche nom+année, dont [buildSearchQuery] ramène déjà `platforms.name` dans le même appel.
 */
fun buildPlatformsQuery(igdbGameId: Long): String =
    "fields platforms.name; where id = $igdbGameId; limit 1;"

private fun escapeQueryText(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * Étape 2 de la cascade de correspondance (voir en tête de fichier) : à défaut d'App ID Steam
 * exploitable, on retrouve le jeu par similarité de titre parmi les candidats renvoyés par la
 * recherche IGDB, en utilisant l'année de sortie RAWG pour départager les candidats dont le nom
 * est proche. Renvoie `null` si aucun candidat n'est jugé assez proche plutôt que de forcer une
 * correspondance hasardeuse (le temps de jeu resterait alors "non disponible").
 */
fun findBestMatch(title: String, releaseYear: Int?, candidates: List<IgdbGameDto>): IgdbGameDto? {
    val normalizedTitle = normalizeForMatching(title)

    val closeCandidates = candidates
        .map { it to titleSimilarity(normalizedTitle, normalizeForMatching(it.name)) }
        .filter { (_, similarity) -> similarity >= MATCH_SIMILARITY_THRESHOLD }
    if (closeCandidates.isEmpty()) return null

    // Plusieurs candidats au nom proche (éditions différentes d'un même jeu, par ex.) : on
    // privilégie celui dont l'année de sortie IGDB correspond à l'année RAWG, quand elle est
    // connue. Sans correspondance d'année (ou année RAWG inconnue), on retombe sur la meilleure
    // similarité de nom, comme avant l'introduction de ce signal.
    if (releaseYear != null) {
        closeCandidates
            .filter { (candidate, _) -> igdbReleaseYear(candidate) == releaseYear }
            .maxByOrNull { (_, similarity) -> similarity }
            ?.let { return it.first }
    }

    return closeCandidates.maxByOrNull { (_, similarity) -> similarity }?.first
}

/** Année de sortie IGDB, convertie depuis le timestamp Unix `first_release_date` (UTC). */
fun igdbReleaseYear(game: IgdbGameDto): Int? =
    game.firstReleaseDate?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year }

/** En-dessous de ce seuil (proportion de caractères en commun), le candidat est jugé peu fiable. */
private const val MATCH_SIMILARITY_THRESHOLD = 0.75

/**
 * Retire la casse/ponctuation, mais aussi les suffixes entre parenthèses (années, mentions
 * diverses — ex. IGDB "Persona 3 Reload (2024)" vs RAWG "Persona 3 Reload") et les mentions
 * d'édition courantes ("Deluxe Edition", "Definitive Edition"...), pour que deux titres qui ne
 * diffèrent que par ces mentions soient reconnus comme identiques.
 */
private fun normalizeForMatching(value: String): String {
    val withoutParentheticals = value.replace(PARENTHETICAL_REGEX, " ")
    val withoutEditionSuffix = withoutParentheticals.replace(EDITION_SUFFIX_REGEX, "")
    return withoutEditionSuffix
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() || it == ' ' }
        .replace(Regex(" +"), " ")
        .trim()
}

private val PARENTHETICAL_REGEX = Regex("""\([^)]*\)|\[[^]]*\]""")

private val EDITION_SUFFIX_REGEX = Regex(
    """[:\-–—]\s*(the\s+)?(deluxe|standard|gold|goty|game of the year|definitive|remastered?|""" +
        """complete|enhanced|ultimate|anniversary|special|extended|director'?s cut)(\s+edition)?\s*$""",
    RegexOption.IGNORE_CASE,
)

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

/**
 * Convertit une durée IGDB (secondes) en heures affichables, ou `null` si IGDB n'a pas cette
 * donnée précise pour le jeu. IGDB peut renvoyer `null` (champ absent, ex. "Hastily : N/A") ou `0`
 * pour signifier l'absence de donnée : les deux cas sont traités comme "non disponible", jamais
 * comme "0h".
 */
fun toEstimatedHours(seconds: Int?): Int? = seconds?.takeIf { it > 0 }?.let { it / 3600 }

/**
 * Reformate les plateformes IGDB d'un jeu identifié au même format d'affichage que `Game.platform`
 * (jointes par "/", voir `RawgMappers.formatPlatforms`), mais triées : contrairement à RAWG, l'ordre
 * dans lequel IGDB renvoie ses plateformes n'est pas significatif, autant avoir un résultat
 * déterministe. Renvoie `null` (jamais une chaîne vide) si IGDB n'a aucune plateforme pour ce jeu —
 * `IgdbPlaytimeRepositoryImpl` s'en sert comme signal pour ne jamais écraser `Game.platform` avec
 * une liste vide.
 *
 * Chaque nom passe par [normalizeIgdbPlatformName] avant tri/dédoublonnage : IGDB distingue les OS
 * PC ("PC (Microsoft Windows)", "Mac", "Linux") là où RAWG et l'app ne veulent qu'un seul "PC",
 * peu importe l'OS — sans ce regroupement, un jeu multi-OS afficherait plusieurs cases "PC" à
 * cocher séparément côté `playedPlatforms`, ce qui n'a pas de sens pour l'utilisateur.
 */
fun formatIgdbPlatforms(platforms: List<IgdbPlatformDto>?): String? =
    platforms.orEmpty()
        .map { normalizeIgdbPlatformName(it.name) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/")

/** Noms IGDB (en minuscules) désignant un système d'exploitation de la famille PC. */
private val PC_FAMILY_PLATFORM_NAMES = setOf(
    "pc",
    "pc (microsoft windows)",
    "mac",
    "macos",
    "linux",
)

private fun normalizeIgdbPlatformName(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.lowercase(Locale.ROOT) in PC_FAMILY_PLATFORM_NAMES) "PC" else trimmed
}
