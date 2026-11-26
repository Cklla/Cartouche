package fr.cklla.cartouche.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Copie locale d'une ligne de `cartouche.game_description` : un cache de lecture, qui permet de relire
 * la description d'un jeu hors ligne une fois qu'elle a été obtenue.
 *
 * Pas de clé étrangère vers `games` : la synchro recopie le backlog en supprimant puis recréant des
 * lignes, et une cascade effacerait alors des descriptions encore valables. Les lignes orphelines
 * sont nettoyées à la lecture (voir `DescriptionDao.deleteOrphans`). Une description est un cache,
 * pas une donnée à envoyer : elle n'entre jamais dans la file d'envoi des jeux.
 */
@Entity(tableName = "game_description")
data class DescriptionEntity(
    @PrimaryKey val gameId: String,
    val igdbId: Long,
    val summary: String?,
    val summaryFr: String?,
    val translationModel: String?,
)
