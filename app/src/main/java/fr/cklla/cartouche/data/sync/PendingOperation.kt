package fr.cklla.cartouche.data.sync

import fr.cklla.cartouche.data.local.OutboxDao
import fr.cklla.cartouche.data.local.entity.PendingOperationEntity

/**
 * Nature d'une écriture en attente d'envoi. Stockée par son nom dans `pending_operation.kind`.
 */
enum class PendingOperationKind {
    UPSERT_GAME,
    DELETE_GAME,
}

val PendingOperationEntity.operationKind: PendingOperationKind?
    get() = runCatching { PendingOperationKind.valueOf(kind) }.getOrNull()

/**
 * Les deux fonctions suivantes ajoutent une opération à la file ; elles s'appellent dans la même
 * transaction que l'écriture Room qu'elles accompagnent, pour qu'aucune des deux n'existe sans l'autre.
 */
suspend fun OutboxDao.enqueueUpsert(gameId: String) {
    insert(PendingOperationEntity(kind = PendingOperationKind.UPSERT_GAME.name, gameId = gameId))
}

/**
 * Les opérations en attente du même jeu sont retirées : une mise à jour qui précède la suppression
 * n'a plus d'objet, et la rejouer ressusciterait le jeu côté serveur le temps d'un aller-retour.
 */
suspend fun OutboxDao.enqueueDelete(gameId: String) {
    deleteForGame(gameId)
    insert(PendingOperationEntity(kind = PendingOperationKind.DELETE_GAME.name, gameId = gameId))
}
