package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Année reçue par la route de navigation (voir `CartoucheDestinations.recapRoute`) — celle déjà
 * calculée par `StatsViewModel.recapYear` au moment où la carte a été affichée, pas recalculée ici.
 * Les stats elles-mêmes restent calculées en direct sur le backlog courant via [computeStats],
 * jamais figées au moment du clic sur la carte.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameRepository: GameRepository,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[CartoucheDestinations.RECAP_ARG_YEAR])

    val uiState: StateFlow<StatsData> = gameRepository.observeGames()
        .map { games -> computeStats(games, year) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsData(selectedYear = year),
        )
}
