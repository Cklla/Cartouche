package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.remote.firestore.FakeFirestoreGameDataSource
import fr.cklla.cartouche.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Construit un [GameRepositoryImpl] de test, avec des fakes par défaut pour Firestore/l'auth :
 * la plupart des tests de ViewModel n'ont besoin que de Room (via [dao]) et n'observent pas la
 * synchro Firestore elle-même (couverte par `GameRepositoryImplTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun fakeGameRepository(
    dao: GameDao,
    firestoreDataSource: FakeFirestoreGameDataSource = FakeFirestoreGameDataSource(),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
): GameRepository = GameRepositoryImpl(
    gameDao = dao,
    firestoreDataSource = firestoreDataSource,
    authRepository = authRepository,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)
