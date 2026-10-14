package fr.cklla.cartouche.data.remote.supabase

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ligne de la table `cartouche.game`, telle que l'API REST l'échange. Aucune valeur par défaut sur
 * les champs : le sérialiseur n'écrit pas une propriété égale à sa valeur par défaut, et une `rating`
 * repassée à `null` doit bien être envoyée comme `null` pour effacer la note côté serveur.
 * `user_id` est absent : le serveur le remplit avec le compte connecté.
 *
 * `user_playtime_hours`, `notes` et `played_platforms` sont non nuls ici comme dans la base
 * (colonnes `not null`) : envoyer `null` serait refusé (23502), donc abandonné, puis la ligne locale
 * supprimée au miroir suivant.
 */
@Serializable
internal data class GameRow(
    val id: String,
    val title: String,
    val platform: String,
    val genre: String,
    val status: String,
    @SerialName("rawg_id") val rawgId: Long?,
    @SerialName("release_year") val releaseYear: Int?,
    @SerialName("user_playtime_hours") val userPlaytimeHours: Int,
    @SerialName("estimated_playtime_hastily_hours") val estimatedPlaytimeHastilyHours: Int?,
    @SerialName("estimated_playtime_normally_hours") val estimatedPlaytimeNormallyHours: Int?,
    @SerialName("estimated_playtime_completely_hours") val estimatedPlaytimeCompletelyHours: Int?,
    val rating: Int?,
    val notes: String,
    @SerialName("cover_url") val coverUrl: String?,
    @SerialName("completed_at") val completedAt: Long?,
    @SerialName("abandoned_at") val abandonedAt: Long?,
    @SerialName("played_platforms") val playedPlatforms: List<String>,
    @SerialName("igdb_lookup_attempted") val igdbLookupAttempted: Boolean,
)

internal fun Game.toRow() = GameRow(
    id = id,
    title = title,
    platform = platform,
    genre = genre,
    status = status.name,
    rawgId = rawgId,
    releaseYear = releaseYear,
    userPlaytimeHours = userPlaytimeHours,
    estimatedPlaytimeHastilyHours = estimatedPlaytimeHastilyHours,
    estimatedPlaytimeNormallyHours = estimatedPlaytimeNormallyHours,
    estimatedPlaytimeCompletelyHours = estimatedPlaytimeCompletelyHours,
    rating = rating,
    notes = notes,
    coverUrl = coverUrl,
    completedAt = completedAt,
    abandonedAt = abandonedAt,
    // Tableau trié : un ordre stable évite une écriture « différente » pour le même ensemble.
    playedPlatforms = playedPlatforms.sorted(),
    igdbLookupAttempted = igdbLookupAttempted,
)

/**
 * `null` pour une ligne au statut inconnu (ligne écrite par une version plus récente, ou
 * corrompue) : elle est ignorée plutôt que de fabriquer un [Game] à moitié valide. Limite acceptée :
 * le miroir supprime alors la ligne locale correspondante.
 */
internal fun GameRow.toGameOrNull(): Game? {
    val gameStatus = runCatching { GameStatus.valueOf(status) }.getOrNull() ?: return null
    return Game(
        id = id,
        title = title,
        platform = platform,
        genre = genre,
        status = gameStatus,
        rawgId = rawgId,
        releaseYear = releaseYear,
        userPlaytimeHours = userPlaytimeHours,
        estimatedPlaytimeHastilyHours = estimatedPlaytimeHastilyHours,
        estimatedPlaytimeNormallyHours = estimatedPlaytimeNormallyHours,
        estimatedPlaytimeCompletelyHours = estimatedPlaytimeCompletelyHours,
        rating = rating,
        notes = notes,
        // Cette URL finit directement dans un chargeur d'images : on n'accepte que du HTTPS,
        // plutôt que de charger n'importe quel schéma présent en base.
        coverUrl = coverUrl?.takeIf { it.startsWith("https://") },
        completedAt = completedAt,
        abandonedAt = abandonedAt,
        playedPlatforms = playedPlatforms.toSet(),
        igdbLookupAttempted = igdbLookupAttempted,
    )
}

/**
 * Lit toutes les pages d'une requête. L'API plafonne le nombre de lignes renvoyées par requête
 * (1000 par défaut) : une lecture unique tronquerait silencieusement un backlog un peu fourni, et la
 * synchro prendrait les lignes manquantes pour des suppressions. Une page incomplète marque la fin ;
 * une exception à n'importe quelle page fait échouer toute la lecture.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int,
    fetchPage: suspend (from: Long, to: Long) -> List<T>,
): List<T> {
    val all = mutableListOf<T>()
    var from = 0L
    while (true) {
        val page = fetchPage(from, from + pageSize - 1)
        all += page
        if (page.size < pageSize) return all
        from += pageSize
    }
}

/**
 * Code d'erreur (SQLSTATE renvoyé par PostgREST) d'une écriture rejetée sur le fond : données
 * invalides (classe 22) ou contrainte d'intégrité violée (classe 23 : CHECK, NOT NULL…). Rejouée,
 * elle échouerait à l'identique.
 *
 * Le statut HTTP ne suffit pas : un 400 couvre aussi une colonne inconnue du serveur (`PGRST204`,
 * application plus récente que le schéma déployé), un 403 un droit mal configuré, autant d'erreurs
 * qui disparaissent une fois le serveur corrigé. Tout ce qui n'est pas classé ici se réessaie :
 * abandonner à tort une écriture est une perte de données (le miroir efface ensuite la ligne
 * locale que le serveur n'a jamais reçue), la réessayer à tort est seulement lent.
 */
internal fun isPermanentFailure(code: String?): Boolean =
    code != null && code.length == SQLSTATE_LENGTH && (code.startsWith("22") || code.startsWith("23"))

private const val SQLSTATE_LENGTH = 5
