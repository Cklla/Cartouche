package fr.cklla.cartouche.domain.description

import fr.cklla.cartouche.domain.model.igdbIdOrNull

// Description d'un jeu : texte de présentation d'IGDB et sa traduction française, gardés dans la
// table `cartouche.game_description` (une ligne par jeu, lue seulement à l'ouverture d'une fiche).
// Ce fichier ne contient que de la logique pure : modèle, bornes et règle de repli. La règle de
// déclenchement est dans `DescriptionResolver`, les accès réseau et Room dans la couche data.

// Bornes des `check` de la table, en caractères (`char_length` de Postgres compte des points de code).
const val MAX_SUMMARY_LENGTH = 5000
const val MAX_SUMMARY_FR_LENGTH = 10_000
const val MAX_TRANSLATION_MODEL_LENGTH = 100

/**
 * Contenu d'une description : ce que la fonction renvoie et ce que la table garde, sans l'id du jeu.
 *
 * [igdbId] est l'identifiant IGDB dont le texte est tiré : s'il diffère de celui du jeu, la description
 * est périmée. [summary] à `null` veut dire « IGDB n'a pas de description » (résultat définitif).
 * [summaryFr] reste `null` sans traduction, et toujours sans original.
 */
data class DescriptionContent(
    val igdbId: Long,
    val summary: String?,
    val summaryFr: String?,
    val translationModel: String?,
)

/** Description enregistrée pour le jeu [gameId]. */
data class GameDescription(val gameId: String, val content: DescriptionContent) {
    /** Une description ne vaut que pour l'id IGDB dont elle est tirée. */
    fun isCurrent(igdbId: Long): Boolean = content.igdbId == igdbId
}

/** Contenu « pas de description exploitable » : IGDB n'en a pas, ou la table a refusé le texte. */
fun emptyContent(igdbId: Long) = DescriptionContent(igdbId, summary = null, summaryFr = null, translationModel = null)

private fun String.codePointLength() = codePointCount(0, length)

/**
 * Vrai si le contenu respecte les `check` de la table, plus ce que la table ne dit pas : un texte vide
 * ou blanc n'est jamais enregistré (« pas de description » s'écrit `null`, jamais `''`), et une
 * traduction ne va pas sans original ni sans le modèle qui l'a produite. Le même contrôle sert à
 * l'écriture, à la lecture (une ligne posée par un autre client) et à la réponse de la fonction.
 */
fun DescriptionContent.isWithinLimits(): Boolean {
    if (igdbIdOrNull(igdbId) == null) return false
    val original = summary ?: return summaryFr == null && translationModel == null
    if (original.isBlank() || original.codePointLength() > MAX_SUMMARY_LENGTH) return false
    val translation = summaryFr ?: return translationModel == null
    if (translation.isBlank() || translation.codePointLength() > MAX_SUMMARY_FR_LENGTH) return false
    val model = translationModel ?: return false
    return model.isNotEmpty() && model.codePointLength() <= MAX_TRANSLATION_MODEL_LENGTH
}

// Un texte long est replié derrière « Afficher plus » (une dizaine de mots par ligne de fiche, cinq
// lignes visibles) ; les sauts de ligne comptent comme des lignes. Mêmes seuils que le site.
const val FOLD_LENGTH = 280
const val FOLD_LINES = 4

/** Vrai si le texte est assez long pour être replié. */
fun needsFold(text: String): Boolean = text.codePointLength() > FOLD_LENGTH || text.split('\n').size > FOLD_LINES

/** Ce que la fiche montre. */
sealed interface DescriptionView {
    /** Pas de section : jeu sans id IGDB, ou IGDB n'a pas de texte. */
    data object None : DescriptionView

    /**
     * Rien n'est encore connu de la description (ou la lecture a échoué) : la section est montrée repliée,
     * et c'est son déroulement qui lance la demande de traduction, jamais l'ouverture de la fiche.
     */
    data object Collapsed : DescriptionView

    data object Loading : DescriptionView

    /** Panne de lecture ou de traduction : rien n'est écrit, la description sera redemandée à la prochaine ouverture de la fiche, au déroulement. */
    data object Failed : DescriptionView

    /** [saved] faux : le texte est affiché mais son enregistrement a échoué (redemandé à la prochaine ouverture de la fiche, au déroulement). */
    data class Ready(val summary: String, val summaryFr: String?, val saved: Boolean) : DescriptionView
}

fun DescriptionContent.toView(saved: Boolean): DescriptionView =
    summary?.let { DescriptionView.Ready(summary = it, summaryFr = summaryFr, saved = saved) } ?: DescriptionView.None

/**
 * Issue d'une demande à la fonction, à trois états comme la lecture IGDB des durées : « IGDB n'a pas de
 * description » est définitif et s'enregistre ; une panne n'écrit rien.
 */
sealed interface DescriptionRequestResult {
    data class Found(val content: DescriptionContent) : DescriptionRequestResult

    data class NotFound(val content: DescriptionContent) : DescriptionRequestResult

    data object Failed : DescriptionRequestResult
}

/** Issue d'une écriture sur le serveur. */
enum class WriteOutcome {
    WRITTEN,

    /** Clé primaire déjà prise : un autre appareil a écrit la ligne avant nous. */
    CONFLICT,

    /** Un `check` de la table refuse le texte : définitif, jamais retenté tel quel. */
    REJECTED,

    FAILED,
}

/** Ce que la lecture trouve pour un jeu. */
sealed interface StoredDescription {
    /** Aucune ligne. */
    data object Missing : StoredDescription

    /**
     * Ligne admise par la table mais refusée par ce client (texte blanc, traduction sans modèle…), écrite
     * par un autre client. Traitée comme périmée pour être réparée, jamais comme absente : sinon l'insert
     * échouerait (clé déjà prise) et une traduction payée serait refaite à chaque ouverture.
     */
    data object Unreadable : StoredDescription

    data class Present(val description: GameDescription) : StoredDescription
}
