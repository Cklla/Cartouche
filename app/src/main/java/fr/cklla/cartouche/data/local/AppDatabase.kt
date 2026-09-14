package fr.cklla.cartouche.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.cklla.cartouche.data.local.entity.GameEntity

@Database(entities = [GameEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
