package fr.cklla.cartouche.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.cklla.cartouche.data.local.entity.DescriptionEntity

@Dao
interface DescriptionDao {

    @Query("SELECT * FROM game_description WHERE gameId = :gameId")
    suspend fun getByGameId(gameId: String): DescriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(description: DescriptionEntity)

    @Query("DELETE FROM game_description WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: String)

    // Jeux retirés du backlog (ici ou sur un autre appareil) : le serveur supprime leur description en
    // cascade, le cache local la nettoie ici.
    @Query("DELETE FROM game_description WHERE gameId NOT IN (SELECT id FROM games)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM game_description")
    suspend fun clearAll()
}
