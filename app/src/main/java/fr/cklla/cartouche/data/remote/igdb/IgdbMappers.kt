package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbPlatformDto
import fr.cklla.cartouche.domain.model.hoursOrNull
import fr.cklla.cartouche.domain.model.igdbIdOrNull
import fr.cklla.cartouche.domain.model.joinPlatformsWithinLimit
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Fonctions pures autour d'IGDB (construction de requêtes "Apicalypse", correspondance de titre) :
 * isolées du repository pour rester testables sans appel réseau, même principe que
 * `SearchFiltering` ailleurs dans le projet.
 *
 * Un jeu qui connaît son `igdbId` (ajouté depuis la recherche) se relit directement par id
 * ([buildGameByIdQuery]). Pour un jeu sans id, la correspondance se fait par nom normalisé et année
 * de sortie ([buildSearchQuery], [findBestMatch]) ; sans correspondance fiable, rien n'est modifié.
 * Les règles de filtrage et de fusion des résultats de recherche sont dans `IgdbGameMerge`.
 */

/** Champs demandés pour chaque jeu du catalogue (voir `IgdbGameDto`). */
private const val GAME_FIELDS =
    "name,first_release_date,platforms.name,cover.image_id,genres.name,game_type.type," +
        "version_parent.name,parent_game.name,total_rating_count"

/**
 * Champs de la correspondance par titre : le type et les parents permettent de remonter d'une
 * édition ou d'un portage au jeu de base (voir `IgdbGameMerge.mergeParentId`).
 */
private const val MATCH_FIELDS = "id,name,first_release_date,game_type.type,version_parent.name,parent_game.name"

/** Candidats demandés à IGDB pour une recherche : on en garde 20 au plus après fusion. */
const val SEARCH_CANDIDATE_LIMIT = 40

/**
 * Plafond de lignes pour la lecture des éditions et portages : le maximum accepté par IGDB (500).
 * Les DLC et packs sont aussi liés par `parent_game` et occupent des lignes sans être gardés : un
 * plafond plus bas pouvait faire tronquer des éditions et des portages, donc des plateformes, pour un
 * jeu très chargé en contenus additionnels.
 */
private const val CHILDREN_LIMIT = 500

/**
 * Recherche du catalogue par texte libre. Le filtre `where` est appliqué **côté serveur** (avant
 * `limit`) : filtrer après coup noierait les jeux de base sous les DLC au nom proche. Il garde les
 * jeux de base (0), extensions autonomes (4), remakes (8), remasters (9), jeux étendus (10) et
 * portages (11), **et** ceux dont `game_type` est absent : un filtre ne doit jamais écarter un jeu
 * dont le champ n'est pas renseigné (piège déjà vécu avec l'ancien champ `category`, qui était vide
 * pour des jeux de base). Écartés : DLC, extensions, bundles, mods, épisodes, saisons, forks,
 * packs et mises à jour.
 *
 * `search` n'accepte pas `sort` (HTTP 406) : le tri est fait côté client (`rankByRelevance`).
 */
fun buildCatalogSearchQuery(text: String): String =
    "search \"${escapeQueryText(text)}\"; fields $GAME_FIELDS; " +
        "where game_type = (${KEPT_GAME_TYPE_IDS.joinToString(",")}) | game_type = null; " +
        "limit $SEARCH_CANDIDATE_LIMIT;"

/**
 * Lecture d'un ou plusieurs jeux par id. Les ids sont des entiers validés (voir [igdbIdOrNull]),
 * jamais du texte : rien à échapper, et rien d'autre qu'un entier ne peut entrer dans la requête.
 */
fun buildGamesByIdsQuery(ids: Collection<Long>): String {
    val valid = ids.mapNotNull(::igdbIdOrNull).distinct()
    require(valid.isNotEmpty()) { "aucun identifiant IGDB valide" }
    return "fields $GAME_FIELDS; where id = (${valid.joinToString(",")}); limit ${valid.size};"
}

/** Lecture d'un jeu par id (voir [buildGamesByIdsQuery]). */
fun buildGameByIdQuery(igdbGameId: Long): String = buildGamesByIdsQuery(listOf(igdbGameId))

/**
 * Éditions et portages rattachés à des jeux déjà connus (un niveau). Aucun filtre de type côté
 * serveur : la règle de rattachement (`IgdbGameMerge.mergeParentId`) est appliquée côté client,
 * ce qui évite d'écrire un `where` combiné qu'on n'a pas éprouvé sur des données réelles.
 */
fun buildChildrenQuery(parentIds: Collection<Long>): String {
    val valid = parentIds.mapNotNull(::igdbIdOrNull).distinct()
    require(valid.isNotEmpty()) { "aucun identifiant IGDB valide" }
    val list = valid.joinToString(",")
    return "fields $GAME_FIELDS; where version_parent = ($list) | parent_game = ($list); limit $CHILDREN_LIMIT;"
}

/**
 * Requête de correspondance par titre, pour un jeu qui ne connaît pas encore son id IGDB : jusqu'à
 * 30 candidats, départagés ensuite par [findBestMatch]. Pas de filtre sur `game_type` ici : la
 * similarité de nom suffit à écarter les contenus additionnels (packs de costumes de *Persona 3
 * Reload*, par exemple), et un jeu au `game_type` absent doit rester candidat.
 *
 * [title] passe par [stripDisambiguatingSuffixes] avant de devenir le texte de la clause `search` :
 * `search "God of War (2018)"` renvoie **0 résultat** (confirmé sur l'API), alors que
 * `search "God of War"` retrouve le jeu.
 */
fun buildSearchQuery(title: String): String =
    "search \"${escapeQueryText(stripDisambiguatingSuffixes(title))}\"; fields $MATCH_FIELDS; limit 30;"

/** Requête de durée de vie pour un jeu IGDB déjà identifié. */
fun buildTimeToBeatQuery(igdbGameId: Long): String =
    "fields hastily,normally,completely; where game_id = $igdbGameId; limit 1;"

private fun escapeQueryText(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * Correspondance par titre (voir en tête de fichier) : à défaut d'id IGDB connu
 * exploitable, on retrouve le jeu par similarité de titre parmi les candidats renvoyés par la
 * recherche IGDB, en utilisant l'année de sortie enregistrée pour départager les candidats dont le nom
 * est proche. Renvoie `null` si aucun candidat n'est jugé assez proche plutôt que de forcer une
 * correspondance hasardeuse (le temps de jeu resterait alors "non disponible").
 */
fun findBestMatch(title: String, releaseYear: Int?, candidates: List<IgdbGameDto>): IgdbGameDto? {
    val normalizedTitle = normalizeForMatching(title)

    val closeCandidates = candidates
        .map { it to titleSimilarity(normalizedTitle, normalizeForMatching(it.name.orEmpty())) }
        .filter { (_, similarity) -> similarity >= MATCH_SIMILARITY_THRESHOLD }
    if (closeCandidates.isEmpty()) return null

    // Plusieurs candidats au nom proche (éditions différentes d'un même jeu, par ex.) : on
    // privilégie celui dont l'année de sortie IGDB correspond à l'année enregistrée, quand elle est
    // connue. Sans correspondance d'année (ou année inconnue), on retombe sur la meilleure
    // similarité de nom, comme avant l'introduction de ce signal.
    if (releaseYear != null) {
        closeCandidates
            .filter { (candidate, _) -> igdbReleaseYear(candidate) == releaseYear }
            .maxByOrNull { (_, similarity) -> similarity }
            ?.let { return it.first }
    }

    return closeCandidates.maxByOrNull { (_, similarity) -> similarity }?.first
}

/**
 * Le rapprochement par nom est-il **certain** ? Seul un rapprochement certain autorise à poser un
 * `igdb_id` définitif et à remplacer la jaquette : il faut que le titre normalisé du **jeu de base**
 * (voir `IgdbGateway.resolveBase`) soit identique à celui du jeu enregistré **et** que les années de
 * sortie soient égales. Un titre voisin ("Hades" ~ "Hades II" est déjà écarté par le seuil de
 * [findBestMatch], mais "Persona 3 Reload" ~ "Persona 3 Reload Expansion Pass" ne l'est pas) ou une
 * année différente ne suffit pas, et une année enregistrée inconnue non plus : on ne fixe pas à vie
 * un jeu sur un mauvais id, que la remise à niveau du site ne reproposerait plus.
 */
fun isCertainMatch(title: String, releaseYear: Int?, base: IgdbGameDto): Boolean {
    val normalizedTitle = normalizeForMatching(title)
    return releaseYear != null &&
        igdbReleaseYear(base) == releaseYear &&
        normalizedTitle.isNotEmpty() &&
        normalizedTitle == normalizeForMatching(base.name.orEmpty())
}

/** Année de sortie IGDB, convertie depuis le timestamp Unix `first_release_date` (UTC). */
fun igdbReleaseYear(game: IgdbGameDto): Int? =
    game.firstReleaseDate?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year }

/** En-dessous de ce seuil (proportion de caractères en commun), le candidat est jugé peu fiable. */
private const val MATCH_SIMILARITY_THRESHOLD = 0.75

/**
 * Retire les suffixes entre parenthèses (années, mentions diverses — ex. IGDB "Persona 3 Reload
 * (2024)" vs "Persona 3 Reload" enregistré) et les mentions d'édition courantes ("Deluxe Edition",
 * "Definitive Edition"...), sans toucher à la casse ni au reste de la ponctuation : utilisé à la
 * fois par [normalizeForMatching] (comparaison de candidats déjà renvoyés par IGDB) et par
 * [buildSearchQuery] (texte de la requête envoyée à IGDB) — voir ce dernier pour le cas concret qui
 * a motivé cette réutilisation.
 */
private fun stripDisambiguatingSuffixes(value: String): String =
    value.replace(PARENTHETICAL_REGEX, " ")
        .replace(EDITION_SUFFIX_REGEX, "")
        .replace(Regex(" +"), " ")
        .trim()

/**
 * Retire en plus la casse/ponctuation restante de [stripDisambiguatingSuffixes], pour que deux
 * titres qui ne diffèrent que par ces mentions soient reconnus comme identiques lors de la
 * comparaison de similarité.
 */
internal fun normalizeForMatching(value: String): String =
    stripDisambiguatingSuffixes(value)
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() || it == ' ' }
        .replace(Regex(" +"), " ")
        .trim()

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
fun toEstimatedHours(seconds: Int?): Int? = hoursOrNull(seconds?.takeIf { it > 0 }?.let { it / 3600 })

/**
 * Reformate les plateformes IGDB d'un jeu identifié au même format d'affichage que `Game.platform`
 * (jointes par "/"), mais triées : l'ordre
 * dans lequel IGDB renvoie ses plateformes n'est pas significatif, autant avoir un résultat
 * déterministe. Renvoie `null` (jamais une chaîne vide) si IGDB n'a aucune plateforme pour ce jeu —
 * `IgdbPlaytimeRepositoryImpl` s'en sert comme signal pour ne jamais écraser `Game.platform` avec
 * une liste vide.
 *
 * Chaque nom passe par [normalizeIgdbPlatformName] avant tri/dédoublonnage : IGDB distingue les OS
 * PC ("PC (Microsoft Windows)", "Mac", "Linux") là où l'app ne veut qu'un seul "PC",
 * peu importe l'OS — sans ce regroupement, un jeu multi-OS afficherait plusieurs cases "PC" à
 * cocher séparément côté `playedPlatforms`, ce qui n'a pas de sens pour l'utilisateur.
 */
fun formatIgdbPlatforms(platforms: List<IgdbPlatformDto>?): String? =
    platforms.orEmpty()
        .mapNotNull { it.name }
        .map { normalizeIgdbPlatformName(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .takeIf { it.isNotEmpty() }
        ?.let(::joinPlatformsWithinLimit)

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
