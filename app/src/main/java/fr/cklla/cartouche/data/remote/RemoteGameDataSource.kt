package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.domain.model.Game
import kotlinx.coroutines.flow.Flow

/**
 * Accès au backlog stocké sur le serveur, sur le modèle métier [Game] plutôt que sur des types du
 * SDK. Interface séparée de son implémentation pour pouvoir la remplacer par un fake en test
 * unitaire. Toutes les opérations portent sur le compte connecté : c'est la session qui désigne
 * l'utilisateur, et le serveur n'expose que ses lignes.
 *
 * Les écritures sont idempotentes (voir chaque méthode) : la file d'envoi les rejoue après un succès
 * non confirmé sans rien casser.
 */
interface RemoteGameDataSource {

    /**
     * Signal « quelque chose a peut-être changé sur le serveur » : émis à chaque (re)connexion de
     * l'écoute temps réel, puis à chaque changement. Il ne transporte aucune donnée, l'appelant refait
     * un [fetchAll] complet. Se termine par une exception si l'écoute échoue.
     */
    fun changes(): Flow<Unit>

    /**
     * État complet du backlog. Lève une exception si la lecture échoue ou n'est pas complète : un
     * résultat partiel serait pris pour un serveur qui a supprimé le reste.
     */
    suspend fun fetchAll(): List<Game>

    /** Crée ou remplace le jeu (même identifiant, mêmes champs). */
    suspend fun upsertGame(game: Game)

    /** Supprime le jeu ; ne fait rien s'il n'existe pas. */
    suspend fun deleteGame(gameId: String)
}

/**
 * Écriture refusée définitivement par le serveur (contrainte violée, donnée invalide) : la rejouer
 * donnerait la même réponse. À distinguer d'une panne réseau ou serveur, qui se réessaie.
 */
class PermanentRemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)
