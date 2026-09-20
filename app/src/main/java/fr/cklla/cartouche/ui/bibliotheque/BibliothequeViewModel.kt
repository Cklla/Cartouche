package fr.cklla.cartouche.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.availableCompletedYears
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BibliothequeViewModel @Inject constructor(
    gameRepository: GameRepository,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(BacklogFilter.TOUS)
    private val selectedYear = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<BibliothequeUiState> = combine(
        gameRepository.observeGames(),
        selectedFilter,
        selectedYear,
    ) { games, filter, year ->
        BibliothequeUiState(
            isLoading = false,
            visibleGames = filterGames(games, filter, year),
            selectedFilter = filter,
            filterCounts = countByFilter(games),
            availableCompletedYears = availableCompletedYears(games),
            selectedYear = year,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliothequeUiState(),
    )

    fun onFilterSelected(filter: BacklogFilter) {
        selectedFilter.value = filter
        // Le filtre par année n'a de sens que sous "Terminé" (voir `filterGames`) : changer de
        // filtre de statut repart d'une sélection d'année propre plutôt que de garder un choix
        // invisible.
        selectedYear.value = null
    }

    /** Re-sélectionner l'année déjà active la désélectionne (retour à "Toutes les années"). */
    fun onYearSelected(year: Int?) {
        selectedYear.value = if (year != null && selectedYear.value == year) null else year
    }
}
