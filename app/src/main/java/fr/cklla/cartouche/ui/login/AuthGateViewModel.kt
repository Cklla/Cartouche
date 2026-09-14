package fr.cklla.cartouche.ui.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * N'expose que l'état de connexion, utilisé par `CartoucheApp` pour décider d'afficher
 * [LoginScreen] ou le `NavHost` normal — connexion obligatoire au lancement, pas de mode
 * "backlog local sans compte".
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser
}
