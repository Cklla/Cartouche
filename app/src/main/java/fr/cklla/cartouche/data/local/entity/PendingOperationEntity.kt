package fr.cklla.cartouche.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Écriture locale pas encore confirmée par le serveur (file d'attente d'envoi, ou « outbox »).
 *
 * [seq] donne l'ordre de rejeu. Pas de clé étrangère vers `games` : une suppression en attente doit
 * survivre à la ligne qu'elle supprime.
 *
 * Une opération ne porte que l'identifiant du jeu : l'état envoyé est relu dans Room au moment de
 * l'envoi, donc toujours le plus récent.
 */
@Entity(tableName = "pending_operation")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val kind: String,
    val gameId: String,
)
