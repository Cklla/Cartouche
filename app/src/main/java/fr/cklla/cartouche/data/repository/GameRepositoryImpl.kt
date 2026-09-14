package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implémentation Room du [GameRepository].
 *
 * Pour l'instant, Room est l'unique source de vérité (fonctionnement 100% hors-ligne).
 * La synchro Firestore multi-appareils sera ajoutée dans une étape ultérieure :
 * ce Repository restera le seul endroit à modifier, les ViewModels n'auront rien
 * à changer puisqu'ils ne dépendent que de l'interface [GameRepository].
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
) : GameRepository {

    override fun observeGames(): Flow<List<Game>> =
        gameDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeGame(id: Long): Flow<Game?> =
        gameDao.observeById(id).map { it?.toDomain() }

    override suspend fun addGame(game: Game): Resource<Long> = runCatching {
        gameDao.insert(game.toEntity())
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter le jeu au backlog.", it) },
    )

    override suspend fun updateGame(game: Game): Resource<Unit> = runCatching {
        gameDao.update(game.toEntity())
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour le jeu.", it) },
    )

    override suspend fun deleteGame(id: Long): Resource<Unit> = runCatching {
        gameDao.deleteById(id)
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer le jeu du backlog.", it) },
    )
}
