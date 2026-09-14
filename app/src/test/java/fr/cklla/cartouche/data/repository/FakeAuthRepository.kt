package fr.cklla.cartouche.data.repository

import android.content.Context
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Double de test en mémoire : pas de vrai Credential Manager/Firebase à solliciter ici. */
class FakeAuthRepository(
    user: AuthUser? = AuthUser(uid = "fake-uid", displayName = "Joueur Test"),
    /** Résultat renvoyé par [signIn], configurable au cas par cas selon le test. */
    var signInResult: Resource<AuthUser> = Resource.Error("signIn() non configuré par ce fake"),
) : AuthRepository {

    private val _currentUser = MutableStateFlow(user)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    var signOutCallCount = 0
        private set

    override suspend fun signIn(context: Context): Resource<AuthUser> {
        if (signInResult is Resource.Success) {
            _currentUser.value = (signInResult as Resource.Success).data
        }
        return signInResult
    }

    override suspend fun signOut() {
        signOutCallCount++
        _currentUser.value = null
    }

    /** Simule une connexion déjà effective, sans passer par le flow [signIn] (Credential Manager). */
    fun signInAs(user: AuthUser) {
        _currentUser.value = user
    }
}
