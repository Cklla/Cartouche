package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Faux DAO en mémoire, utilisé pour tester [GameRepositoryImpl] sans base Room réelle
 * (pas besoin d'instrumentation Android pour ces tests).
 */
class FakeGameDao : GameDao {

    private val games = MutableStateFlow<List<GameEntity>>(emptyList())
    private var nextId = 1L

    /** Permet de simuler un échec Room (contrainte violée, disque plein...) dans les tests. */
    var shouldThrowOnInsert = false

    override fun observeAll(): Flow<List<GameEntity>> = games

    override fun observeById(id: Long): Flow<GameEntity?> =
        games.map { list -> list.find { it.id == id } }

    override suspend fun insert(game: GameEntity): Long {
        if (shouldThrowOnInsert) error("Échec Room simulé")
        val id = if (game.id != 0L) game.id else nextId++
        val stored = game.copy(id = id)
        games.update { list -> list.filterNot { it.id == id } + stored }
        return id
    }

    override suspend fun update(game: GameEntity) {
        games.update { list -> list.map { if (it.id == game.id) game else it } }
    }

    override suspend fun deleteById(id: Long) {
        games.update { list -> list.filterNot { it.id == id } }
    }
}
