package fr.cklla.cartouche.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.data.local.AppDatabase
import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.MIGRATION_1_2
import fr.cklla.cartouche.data.local.MIGRATION_2_3
import fr.cklla.cartouche.data.local.MIGRATION_3_4
import fr.cklla.cartouche.data.local.MIGRATION_4_5
import javax.inject.Singleton

/** Fournit la base Room, unique pour toute la durée de vie de l'application. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cartouche.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideGameDao(database: AppDatabase): GameDao = database.gameDao()
}
