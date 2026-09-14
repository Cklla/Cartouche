package fr.cklla.cartouche.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.cklla.cartouche.data.repository.GameRepositoryImpl
import fr.cklla.cartouche.data.repository.GameSearchRepositoryImpl
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.repository.GameSearchRepository
import javax.inject.Singleton

/** Lie les interfaces de repository à leur implémentation concrète, pour que les
 * ViewModels ne dépendent jamais d'une classe concrète. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindGameSearchRepository(impl: GameSearchRepositoryImpl): GameSearchRepository
}
