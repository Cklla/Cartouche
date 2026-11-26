package fr.cklla.cartouche.domain.description

import fr.cklla.cartouche.domain.model.igdbIdOrNull
import kotlinx.coroutines.CancellationException

/** Accès dont la règle de la fiche a besoin ; la couche data les branche sur Room et sur Supabase. */
interface DescriptionSource {
    /** Lit la ligne du jeu. Lève en cas d'erreur (réseau, serveur, ligne en double). */
    suspend fun read(gameId: String): StoredDescription

    /** Demande la description à la fonction. Ne lève jamais : toute panne vaut [DescriptionRequestResult.Failed]. */
    suspend fun request(igdbId: Long): DescriptionRequestResult

    suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome

    /**
     * Remplace la ligne : [WriteOutcome.WRITTEN] seulement si une ligne a été touchée, [WriteOutcome.FAILED]
     * sinon (ligne ou jeu disparus entre-temps), [WriteOutcome.REJECTED] si la table refuse le texte.
     */
    suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome
}

/**
 * Règle de la fiche, identique à celle du site (au plus une demande à la fonction par appel, jamais de
 * boucle) :
 *  1. jeu sans id IGDB valide : rien ;
 *  2. lecture de la ligne ; en cas d'erreur de lecture, rien n'est demandé (la ligne existe peut-être) ;
 *  3. ligne à jour : affichée, la fonction n'est pas appelée ;
 *  4. sinon (aucune ligne, ligne périmée, ou ligne illisible traitée comme périmée pour être réparée) :
 *     une demande. Panne : rien n'est écrit, l'essai n'est pas consommé. Réponse : écrite (insert, ou
 *     update si une ligne existe déjà). Un conflit (autre appareil plus rapide) relit ; un refus de la
 *     table (23514) écrit à la place « pas de description » pour ne plus redemander (la traduction payée
 *     ne serait sinon jamais enregistrée) ; une autre erreur d'écriture laisse le texte affiché, marqué
 *     non enregistré.
 */
suspend fun resolveDescription(gameId: String, igdbId: Long?, source: DescriptionSource): DescriptionView {
    // Un id hors borne ne part jamais vers la fonction : même garde que pour toute entrée.
    val id = igdbIdOrNull(igdbId) ?: return DescriptionView.None

    val stored = try {
        source.read(gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return DescriptionView.Failed
    }
    if (stored is StoredDescription.Present && stored.description.isCurrent(id)) {
        return stored.description.content.toView(saved = true)
    }

    val content = when (val result = source.request(id)) {
        DescriptionRequestResult.Failed -> return DescriptionView.Failed
        is DescriptionRequestResult.Found -> result.content
        is DescriptionRequestResult.NotFound -> result.content
    }

    // `Missing` : aucune ligne, donc insert. Tout autre état (périmée, illisible) : une ligne existe, donc update.
    suspend fun write(next: DescriptionContent) =
        if (stored is StoredDescription.Missing) source.insert(gameId, next) else source.replace(gameId, next)

    var outcome = write(content)

    if (outcome == WriteOutcome.REJECTED) {
        // La table a refusé le texte : « pas de description exploitable » est écrit à la place, définitif.
        return if (write(emptyContent(id)) == WriteOutcome.WRITTEN) DescriptionView.None else DescriptionView.Failed
    }
    if (outcome == WriteOutcome.CONFLICT) {
        try {
            val reread = source.read(gameId)
            if (reread is StoredDescription.Present && reread.description.isCurrent(id)) {
                return reread.description.content.toView(saved = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Relecture impossible : le texte obtenu reste affiché, marqué non enregistré.
        }
        outcome = WriteOutcome.FAILED
    }
    return content.toView(saved = outcome == WriteOutcome.WRITTEN)
}
