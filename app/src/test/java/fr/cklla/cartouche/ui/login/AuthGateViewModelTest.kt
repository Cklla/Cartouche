package fr.cklla.cartouche.ui.login

import fr.cklla.cartouche.data.repository.FakeAuthRepository
import fr.cklla.cartouche.domain.model.AuthState
import fr.cklla.cartouche.domain.model.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGateViewModelTest {

    private val ada = AuthUser(uid = "u1", displayName = "Ada")

    @Test
    fun `le chargement de la session n'est pas pris pour une deconnexion`() {
        val authRepository = FakeAuthRepository(user = null).apply { startInitializing() }

        val viewModel = AuthGateViewModel(authRepository)

        assertEquals(AuthState.Initializing, viewModel.authState.value)
    }

    @Test
    fun `l'etat suit la connexion puis la deconnexion`() {
        val authRepository = FakeAuthRepository(user = null)
        val viewModel = AuthGateViewModel(authRepository)
        assertEquals(AuthState.SignedOut, viewModel.authState.value)

        authRepository.signInAs(ada)
        assertEquals(AuthState.SignedIn(ada), viewModel.authState.value)

        authRepository.dropSessionWithoutSignOut()
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
    }
}
