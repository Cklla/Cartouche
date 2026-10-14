package fr.cklla.cartouche.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.data.local.entity.PendingOperationEntity

@Database(entities = [GameEntity::class, PendingOperationEntity::class], version = 10, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun outboxDao(): OutboxDao
}
