package fr.cklla.cartouche.data.remote.firestore

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus

/**
 * Conversions entre le modèle métier [Game] et la représentation Firestore d'un document.
 *
 * Travaillent sur des `Map<String, Any?>` plutôt que sur `DocumentSnapshot` (classe du SDK
 * Firestore, impossible à instancier dans un test unitaire sans Robolectric/mock) : c'est
 * justement ce que `DocumentSnapshot.data` renvoie, donc testable sans dépendance Firebase.
 *
 * `id` est volontairement absent de la map : c'est l'id du document Firestore lui-même
 * (`.document(game.id)`), pas la peine de le dupliquer en champ.
 */

private const val FIELD_TITLE = "title"
private const val FIELD_PLATFORM = "platform"
private const val FIELD_GENRE = "genre"
private const val FIELD_STATUS = "status"
private const val FIELD_RAWG_ID = "rawgId"
private const val FIELD_RELEASE_YEAR = "releaseYear"
private const val FIELD_USER_PLAYTIME_HOURS = "userPlaytimeHours"
private const val FIELD_ESTIMATED_PLAYTIME_HASTILY_HOURS = "estimatedPlaytimeHastilyHours"
private const val FIELD_ESTIMATED_PLAYTIME_NORMALLY_HOURS = "estimatedPlaytimeNormallyHours"
private const val FIELD_ESTIMATED_PLAYTIME_COMPLETELY_HOURS = "estimatedPlaytimeCompletelyHours"
private const val FIELD_RATING = "rating"
private const val FIELD_NOTES = "notes"
private const val FIELD_COVER_URL = "coverUrl"

fun Game.toFirestoreMap(): Map<String, Any?> = mapOf(
    FIELD_TITLE to title,
    FIELD_PLATFORM to platform,
    FIELD_GENRE to genre,
    FIELD_STATUS to status.name,
    FIELD_RAWG_ID to rawgId,
    FIELD_RELEASE_YEAR to releaseYear,
    FIELD_USER_PLAYTIME_HOURS to userPlaytimeHours,
    FIELD_ESTIMATED_PLAYTIME_HASTILY_HOURS to estimatedPlaytimeHastilyHours,
    FIELD_ESTIMATED_PLAYTIME_NORMALLY_HOURS to estimatedPlaytimeNormallyHours,
    FIELD_ESTIMATED_PLAYTIME_COMPLETELY_HOURS to estimatedPlaytimeCompletelyHours,
    FIELD_RATING to rating,
    FIELD_NOTES to notes,
    FIELD_COVER_URL to coverUrl,
)

// Firestore n'a pas de type `Int` natif (tout nombre entier remonte en `Long`) : cast via `Number`
// plutôt que `Long` strict, pour rester correct aussi bien face à un vrai document Firestore
// qu'à une map construite à la main en Kotlin (tests). Un statut absent ou inconnu (document
// corrompu, champ renommé côté futur) fait échouer tout le mapping plutôt que de fabriquer un
// `Game` à moitié valide.
fun mapToGame(id: String, data: Map<String, Any?>): Game? {
    val status = (data[FIELD_STATUS] as? String)?.let { raw ->
        runCatching { GameStatus.valueOf(raw) }.getOrNull()
    } ?: return null
    val title = data[FIELD_TITLE] as? String ?: return null
    val platform = data[FIELD_PLATFORM] as? String ?: return null
    val genre = data[FIELD_GENRE] as? String ?: return null

    return Game(
        id = id,
        title = title,
        platform = platform,
        genre = genre,
        status = status,
        rawgId = (data[FIELD_RAWG_ID] as? Number)?.toLong(),
        releaseYear = (data[FIELD_RELEASE_YEAR] as? Number)?.toInt(),
        userPlaytimeHours = (data[FIELD_USER_PLAYTIME_HOURS] as? Number)?.toInt() ?: 0,
        estimatedPlaytimeHastilyHours = (data[FIELD_ESTIMATED_PLAYTIME_HASTILY_HOURS] as? Number)?.toInt(),
        estimatedPlaytimeNormallyHours = (data[FIELD_ESTIMATED_PLAYTIME_NORMALLY_HOURS] as? Number)?.toInt(),
        estimatedPlaytimeCompletelyHours = (data[FIELD_ESTIMATED_PLAYTIME_COMPLETELY_HOURS] as? Number)?.toInt(),
        rating = (data[FIELD_RATING] as? Number)?.toInt(),
        notes = data[FIELD_NOTES] as? String ?: "",
        coverUrl = data[FIELD_COVER_URL] as? String,
    )
}
