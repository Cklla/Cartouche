package fr.cklla.cartouche.domain.model

/**
 * Bornes acceptées par le serveur (contraintes `CHECK` de la table `cartouche.game`). Une écriture qui
 * en sort est refusée définitivement : l'opération est abandonnée, puis la synchro supprime le jeu
 * local que le serveur n'a jamais reçu. Mieux vaut donc ramener une valeur dans la borne que la
 * laisser partir : la perte d'un bout de texte est préférable à la perte du jeu entier.
 */
const val MAX_TITLE_LENGTH = 300
const val MAX_PLATFORM_LENGTH = 200
const val MAX_GENRE_LENGTH = 100
const val MAX_NOTES_LENGTH = 5000
const val MAX_COVER_URL_LENGTH = 2000
const val MAX_PLAYED_PLATFORMS = 20
const val MAX_HOURS = 100_000
const val MIN_RELEASE_YEAR = 1950
const val MAX_RELEASE_YEAR = 2999

private const val PLATFORM_SEPARATOR = "/"

/**
 * Joint des noms de plateformes en une chaîne d'au plus [MAX_PLATFORM_LENGTH] caractères. Les noms
 * qui ne tiennent plus sont écartés en entier, jamais coupés au milieu : une plateforme tronquée
 * ("PlayStat") serait une case à cocher qui n'existe nulle part.
 */
fun joinPlatformsWithinLimit(names: List<String>): String {
    val kept = mutableListOf<String>()
    var length = 0
    for (name in names) {
        val added = if (kept.isEmpty()) name.length else PLATFORM_SEPARATOR.length + name.length
        if (length + added > MAX_PLATFORM_LENGTH) break
        kept += name
        length += added
    }
    return kept.joinToString(PLATFORM_SEPARATOR)
}

/** Année de sortie plausible, ou `null` : une année hors bornes serait refusée par le serveur. */
fun releaseYearOrNull(year: Int?): Int? = year?.takeIf { it in MIN_RELEASE_YEAR..MAX_RELEASE_YEAR }

/** Durée en heures dans la borne du serveur, ou `null` si elle la dépasse (donnée de source douteuse). */
fun hoursOrNull(hours: Int?): Int? = hours?.takeIf { it in 0..MAX_HOURS }

/**
 * Ramène [this] dans les bornes du serveur, sans jamais lever : dernier filet avant l'envoi, quelle
 * que soit l'origine de la valeur (saisie, RAWG, IGDB, ligne déjà présente dans Room avant ces
 * bornes). Les textes sont tronqués, une année ou une durée hors borne passe à `null`, une URL trop
 * longue est écartée (tronquée, elle ne désignerait plus rien).
 */
fun Game.withinServerLimits(): Game = copy(
    title = title.take(MAX_TITLE_LENGTH),
    platform = if (platform.length <= MAX_PLATFORM_LENGTH) platform else joinPlatformsWithinLimit(platform.split(PLATFORM_SEPARATOR)),
    genre = genre.take(MAX_GENRE_LENGTH),
    notes = notes.take(MAX_NOTES_LENGTH),
    coverUrl = coverUrl?.takeIf { it.length <= MAX_COVER_URL_LENGTH },
    releaseYear = releaseYearOrNull(releaseYear),
    userPlaytimeHours = userPlaytimeHours.coerceIn(0, MAX_HOURS),
    estimatedPlaytimeHastilyHours = hoursOrNull(estimatedPlaytimeHastilyHours),
    estimatedPlaytimeNormallyHours = hoursOrNull(estimatedPlaytimeNormallyHours),
    estimatedPlaytimeCompletelyHours = hoursOrNull(estimatedPlaytimeCompletelyHours),
    playedPlatforms = if (playedPlatforms.size <= MAX_PLAYED_PLATFORMS) playedPlatforms else playedPlatforms.sorted().take(MAX_PLAYED_PLATFORMS).toSet(),
)
