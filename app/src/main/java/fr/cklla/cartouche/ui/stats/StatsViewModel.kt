package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.repository.AuthRepository
import fr.cklla.cartouche.domain.repository.GameRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le compte connecté et la déconnexion vivent ici plutôt que dans un ViewModel dédié : Stats est
 * l'écran "vue d'ensemble" de l'app (aucune maquette ne couvre la gestion de compte, ajoutée
 * après le design handoff), un endroit raisonnable pour ce genre d'action ponctuelle plutôt que
 * de créer un écran Réglages entier pour une seule action.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    gameRepository: GameRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val selectedYear = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<StatsData> = combine(
        gameRepository.observeGames(),
        selectedYear,
    ) { games, year -> computeStats(games, year) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsData(),
        )

    /**
     * Année ciblée par la carte "Récap" (voir [recapTargetYear]), `null` la majeure partie de
     * l'année : pas de `StateFlow`, cette valeur ne change pas en cours de vie du ViewModel
     * (l'app n'est pas censée rester ouverte à cheval sur minuit le jour où la fenêtre bascule).
     */
    val recapYear: Int? = recapTargetYear(LocalDate.now())

    /**
     * Stats de la carte "Récap", calculées en direct sur [recapYear] via [computeStats] — jamais
     * de snapshot ni de valeur mise en cache, les jeux terminés/abandonnés jusqu'au dernier jour de
     * l'année ciblée comptent normalement. Ne s'abonne au backlog que si [recapYear] est renseigné,
     * pour ne pas payer une collecte inutile le reste de l'année.
     */
    val recapStats: StateFlow<StatsData> = recapYear?.let { year ->
        gameRepository.observeGames()
            .map { games -> computeStats(games, year) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsData(selectedYear = year),
            )
    } ?: MutableStateFlow(StatsData())

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    /** Re-sélectionner l'année déjà active la désélectionne (retour aux stats toutes années). */
    fun onYearSelected(year: Int?) {
        selectedYear.value = if (year != null && selectedYear.value == year) null else year
    }

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
