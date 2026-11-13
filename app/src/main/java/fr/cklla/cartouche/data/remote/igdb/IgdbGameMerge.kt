package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbPlatformDto
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.MAX_GENRE_LENGTH
import fr.cklla.cartouche.domain.model.MAX_TITLE_LENGTH
import fr.cklla.cartouche.domain.model.igdbIdOrNull

/**
 * Règles de filtrage, de fusion, d'ordre et de jaquette des résultats IGDB. Fonctions pures,
 * sans réseau, pour rester testables : les requêtes qui alimentent ces règles sont dans
 * `IgdbMappers`, leur enchaînement dans `IgdbGateway`.
 *
 * Ces règles ont été fixées sur des données réelles (jeux de base, éditions Switch 2, remakes
 * d'*Ocarina of Time*, *Xenoblade*) ; le principe qui les gouverne : **un champ absent n'écarte
 * jamais un jeu**.
 */

/**
 * Types de jeu (`game_types`) gardés dans les résultats : 0 jeu de base, 4 extension autonome,
 * 8 remake, 9 remaster, 10 jeu étendu, 11 portage. Les autres (1 DLC, 2 extension, 3 bundle, 5 mod,
 * 6 épisode, 7 saison, 12 fork, 13 pack, 14 mise à jour) sont écartés. Un jeu **sans** `game_type`
 * est gardé (voir [isKeptGameType]).
 */
val KEPT_GAME_TYPE_IDS = listOf(0, 4, 8, 9, 10, 11)

private val KEPT_GAME_TYPE_SET = KEPT_GAME_TYPE_IDS.map { it.toLong() }.toSet()

private const val TYPE_MAIN_GAME = 0L
private const val TYPE_EXPANDED_GAME = 10L
private const val TYPE_PORT = 11L

/** Nombre maximal de résultats renvoyés à l'écran après fusion. */
const val MAX_SEARCH_RESULTS = 20

/** Une édition peut elle-même avoir une édition (ex. une « Nintendo Switch 2 Edition ») : deux niveaux. */
const val MAX_MERGE_DEPTH = 2

/** Garde ce jeu ? Faux seulement pour un type explicitement exclu ; un type absent est gardé. */
fun IgdbGameDto.isKeptGameType(): Boolean = gameType == null || gameType.id in KEPT_GAME_TYPE_SET

/**
 * Id du jeu de base auquel ce jeu est rattaché, ou `null` s'il est lui-même un jeu à part entière.
 *
 * - une **édition** (`version_parent`, type « jeu de base » ou absent) se rattache à son parent ;
 * - un **portage** ou un **jeu étendu** (types 11 et 10) se rattache à son `parent_game`
 *   (à défaut, à son `version_parent`) ;
 * - les **remakes** (8), **remasters** (9) et **extensions autonomes** (4) restent des fiches
 *   à part, même quand IGDB leur donne un parent : *Ocarina of Time* (1029), son remake 3DS et
 *   son remake Switch 2 sont trois jeux distincts.
 */
fun IgdbGameDto.mergeParentId(): Long? = when (gameType?.id) {
    null, TYPE_MAIN_GAME -> versionParent?.id
    TYPE_EXPANDED_GAME, TYPE_PORT -> parentGame?.id ?: versionParent?.id
    else -> null
}

/** Un jeu de base avec les plateformes de toute sa famille (éditions et portages, sur [MAX_MERGE_DEPTH] niveaux). */
data class MergedGame(
    val game: IgdbGameDto,
    val platforms: List<IgdbPlatformDto>,
)

/**
 * Id du jeu de base de [game] : on remonte les parents **présents dans [poolById]** (au plus
 * [MAX_MERGE_DEPTH] fois). Un parent absent du lot (type exclu, introuvable) laisse le jeu
 * afficher sa propre fiche plutôt que de le faire disparaître.
 */
private fun rootIdOf(game: IgdbGameDto, poolById: Map<Long, IgdbGameDto>): Long {
    var current = game
    repeat(MAX_MERGE_DEPTH) {
        val parent = current.mergeParentId()?.let(poolById::get) ?: return current.id
        current = parent
    }
    return current.id
}

/**
 * Regroupe le lot [pool] par jeu de base. Le titre et l'identité du résultat sont ceux du jeu de
 * base ; ses plateformes sont l'union des siennes et de celles de ses éditions et portages.
 * L'ordre du lot est conservé pour les jeux de base (avant tri par [rankByRelevance]).
 */
fun buildMergedGames(pool: Collection<IgdbGameDto>): List<MergedGame> {
    val poolById = pool.associateBy { it.id }
    val familyByRoot = pool.groupBy { rootIdOf(it, poolById) }
    return pool
        .filter { rootIdOf(it, poolById) == it.id }
        .map { root ->
            MergedGame(
                game = root,
                platforms = familyByRoot.getValue(root.id).flatMap { it.platforms.orEmpty() },
            )
        }
}

/**
 * Ordre des résultats : titre normalisé **identique** à la recherche, puis titre qui **commence**
 * par la recherche, puis nombre de notes décroissant (absent = 0). `follows` et `hypes` sont à 0
 * presque partout : inutilisables. Le tri est stable : à critères égaux, l'ordre d'IGDB (similarité)
 * est conservé.
 */
fun rankByRelevance(query: String, games: List<MergedGame>): List<MergedGame> {
    val normalizedQuery = normalizeForMatching(query)
    fun tier(game: MergedGame): Int {
        val title = normalizeForMatching(game.game.name)
        return when {
            title == normalizedQuery -> 0
            title.startsWith(normalizedQuery) -> 1
            else -> 2
        }
    }
    return games.sortedWith(
        compareBy<MergedGame> { tier(it) }.thenByDescending { it.game.totalRatingCount ?: 0 },
    )
}

private const val COVER_URL_PREFIX = "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/"
private const val MAX_IMAGE_ID_LENGTH = 40
private val IMAGE_ID_REGEX = Regex("^[a-z0-9]+$")

/**
 * URL de jaquette HTTPS sur `images.igdb.com`, construite **par notre code** à partir d'un
 * `image_id` validé (minuscules et chiffres seulement, longueur bornée) : la valeur renvoyée par
 * le réseau n'est jamais utilisée comme URL telle quelle. `null` si l'id est absent ou invalide
 * (jaquette absente : l'ancienne n'est pas écrasée, voir `applyIgdbEnrichment`). Taille
 * `cover_big_2x` (528×704) : jaquette portrait, nette sur écran dense.
 */
fun buildCoverUrl(imageId: String?): String? =
    imageId
        ?.takeIf { it.length <= MAX_IMAGE_ID_LENGTH && IMAGE_ID_REGEX.matches(it) }
        ?.let { "$COVER_URL_PREFIX$it.jpg" }

/** Année de sortie (UTC) en texte, ou chaîne vide si IGDB n'a pas de date. */
fun yearTextOrEmpty(game: IgdbGameDto): String = igdbReleaseYear(game)?.toString().orEmpty()

/** Premier genre IGDB, affiché tel quel (en anglais), borné à la taille acceptée par le serveur. */
fun firstGenreName(game: IgdbGameDto): String =
    game.genres.orEmpty().firstOrNull()?.name?.take(MAX_GENRE_LENGTH).orEmpty()

/**
 * Résultat de recherche d'un jeu de base fusionné. `null` si son id sort de la borne acceptée par
 * le serveur : un jeu qu'on ne pourrait pas enregistrer n'a pas à être proposé.
 */
fun MergedGame.toSearchResult(): GameSearchResult? {
    val id = igdbIdOrNull(game.id) ?: return null
    return GameSearchResult(
        igdbId = id,
        title = game.name.take(MAX_TITLE_LENGTH),
        platform = formatIgdbPlatforms(platforms).orEmpty(),
        genre = firstGenreName(game),
        year = yearTextOrEmpty(game),
        coverUrl = buildCoverUrl(game.cover?.imageId),
    )
}
