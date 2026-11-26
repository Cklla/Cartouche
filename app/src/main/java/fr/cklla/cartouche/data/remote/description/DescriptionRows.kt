package fr.cklla.cartouche.data.remote.description

import fr.cklla.cartouche.data.local.entity.DescriptionEntity
import fr.cklla.cartouche.domain.description.DescriptionContent
import fr.cklla.cartouche.domain.description.DescriptionRequestResult
import fr.cklla.cartouche.domain.description.GameDescription
import fr.cklla.cartouche.domain.description.StoredDescription
import fr.cklla.cartouche.domain.description.emptyContent
import fr.cklla.cartouche.domain.description.isWithinLimits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ligne de `cartouche.game_description` telle que l'API REST l'échange. Aucune valeur par défaut :
 * le sérialiseur n'écrit pas une propriété égale à sa valeur par défaut, or un texte repassé à `null`
 * doit bien partir comme `null` pour effacer l'ancien. `user_id` et `created_at` ne sont ni lus ni
 * écrits : le serveur remplit le premier avec le compte connecté.
 *
 * Aucun champ n'est facultatif à la lecture : une ligne mal typée ou incomplète lève, et c'est une
 * erreur de lecture, jamais une description à moitié valide.
 */
@Serializable
internal data class DescriptionRow(
    @SerialName("game_id") val gameId: String,
    @SerialName("igdb_id") val igdbId: Long,
    val summary: String?,
    @SerialName("summary_fr") val summaryFr: String?,
    @SerialName("translation_model") val translationModel: String?,
)

/** Colonnes d'un `update` de remplacement (description périmée) : celles du contenu, sans `game_id`. */
@Serializable
internal data class DescriptionPatchRow(
    @SerialName("igdb_id") val igdbId: Long,
    val summary: String?,
    @SerialName("summary_fr") val summaryFr: String?,
    @SerialName("translation_model") val translationModel: String?,
)

/**
 * Réponse de la fonction `cartouche-igdb-description`. Les champs en plus (`truncated`) sont ignorés
 * par le décodeur ; un champ absent ou mal typé fait lever le décodage, que l'appelant traite en panne.
 */
@Serializable
internal data class DescriptionFunctionResponse(
    val igdbId: Long,
    val summary: String?,
    val summaryFr: String?,
    val model: String?,
)

/**
 * Convertit une ligne lue : [StoredDescription.Present] si elle respecte les bornes, sinon
 * [StoredDescription.Unreadable] (une ligne écrite par un autre client ne doit jamais faire planter la
 * fiche ni afficher un texte aberrant, ni être prise pour absente).
 */
internal fun DescriptionRow.toStored(): StoredDescription {
    val description = GameDescription(gameId, DescriptionContent(igdbId, summary, summaryFr, translationModel))
    return if (description.content.isWithinLimits()) StoredDescription.Present(description) else StoredDescription.Unreadable
}

/** Ligne à insérer, ou `null` si le contenu sort des bornes : rien ne part alors vers le serveur. */
internal fun DescriptionContent.toRowOrNull(gameId: String): DescriptionRow? =
    takeIf { it.isWithinLimits() }?.let { DescriptionRow(gameId, igdbId, summary, summaryFr, translationModel) }

internal fun DescriptionContent.toPatchOrNull(): DescriptionPatchRow? =
    takeIf { it.isWithinLimits() }?.let { DescriptionPatchRow(igdbId, summary, summaryFr, translationModel) }

/**
 * Lit la réponse de la fonction. Jamais de confiance : elle doit porter l'id demandé, des textes dans
 * les bornes, et soit ne rien contenir (`summary` nul : IGDB n'a pas de description), soit un original
 * accompagné de sa traduction et du modèle. Toute autre forme vaut [DescriptionRequestResult.Failed].
 */
internal fun DescriptionFunctionResponse.toResult(requestedIgdbId: Long): DescriptionRequestResult {
    if (igdbId != requestedIgdbId) return DescriptionRequestResult.Failed
    val content = DescriptionContent(igdbId, summary, summaryFr, model)
    if (!content.isWithinLimits()) return DescriptionRequestResult.Failed
    return when {
        content.summary == null -> DescriptionRequestResult.NotFound(emptyContent(igdbId))
        // Un original sans traduction n'est pas une réponse de la fonction : elle traduit ou échoue.
        content.summaryFr == null -> DescriptionRequestResult.Failed
        else -> DescriptionRequestResult.Found(content)
    }
}

internal fun DescriptionEntity.toStored(): StoredDescription {
    val description = GameDescription(gameId, DescriptionContent(igdbId, summary, summaryFr, translationModel))
    return if (description.content.isWithinLimits()) StoredDescription.Present(description) else StoredDescription.Unreadable
}

internal fun GameDescription.toEntity() = DescriptionEntity(
    gameId = gameId,
    igdbId = content.igdbId,
    summary = content.summary,
    summaryFr = content.summaryFr,
    translationModel = content.translationModel,
)
