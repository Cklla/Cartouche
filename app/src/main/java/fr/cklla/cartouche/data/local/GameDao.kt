package fr.cklla.cartouche.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fr.cklla.cartouche.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: String): Flow<GameEntity?>

    // L'id (UUID) est déjà renseigné par l'appelant avant insertion (voir `GameRepositoryImpl`) :
    // pas de valeur générée à récupérer, contrairement à l'ancien id auto-incrémenté.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GameEntity)

    @Update
    suspend fun update(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM games")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM games")
    suspend fun clearAll()
}
