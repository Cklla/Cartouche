package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import java.util.UUID
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

    override fun observeGame(id: String): Flow<Game?> =
        gameDao.observeById(id).map { it?.toDomain() }

    // Le UUID est généré ici, avant la première écriture, plutôt que délégué à Room (qui ne sait
    // pas auto-générer un id texte) : c'est aussi le seul endroit qui écrira bientôt à la fois
    // dans Room et dans Firestore, les deux doivent donc partager exactement le même id.
    override suspend fun addGame(game: Game): Resource<String> = runCatching {
        val id = game.id.ifBlank { UUID.randomUUID().toString() }
        gameDao.insert(game.copy(id = id).toEntity())
        id
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

    override suspend fun deleteGame(id: String): Resource<Unit> = runCatching {
        gameDao.deleteById(id)
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer le jeu du backlog.", it) },
    )
}
