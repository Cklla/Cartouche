package fr.cklla.cartouche.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.data.repository.GameRepositoryImpl
import fr.cklla.cartouche.domain.repository.GameRepository
import javax.inject.Singleton

/** Lie l'interface [GameRepository] à son implémentation Room, pour que les ViewModels
 * ne dépendent jamais d'une classe concrète. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository
}
