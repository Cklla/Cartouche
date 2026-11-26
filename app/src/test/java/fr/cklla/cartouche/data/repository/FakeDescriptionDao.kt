package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.DescriptionDao
import fr.cklla.cartouche.data.local.entity.DescriptionEntity

/**
 * Faux DAO en mémoire du cache des descriptions. [knownGameIds] simule la table `games` pour le
 * nettoyage des orphelines ; `null` veut dire « tous les jeux existent ».
 */
class FakeDescriptionDao(var knownGameIds: Set<String>? = null) : DescriptionDao {

    val rows = linkedMapOf<String, DescriptionEntity>()
    var failOnDeleteOrphans = false

    override suspend fun getByGameId(gameId: String): DescriptionEntity? = rows[gameId]

    override suspend fun put(description: DescriptionEntity) {
        rows[description.gameId] = description
    }

    override suspend fun deleteByGameId(gameId: String) {
        rows.remove(gameId)
    }

    override suspend fun deleteOrphans() {
        check(!failOnDeleteOrphans) { "nettoyage en échec" }
        val known = knownGameIds ?: return
        rows.keys.retainAll(known)
    }

    override suspend fun clearAll() {
        rows.clear()
    }
}
