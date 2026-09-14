package fr.cklla.cartouche.ui.bibliotheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.repository.GameRepository
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

    val uiState: StateFlow<BibliothequeUiState> = combine(
        gameRepository.observeGames(),
        selectedFilter,
    ) { games, filter ->
        BibliothequeUiState(
            isLoading = false,
            visibleGames = filterGames(games, filter),
            selectedFilter = filter,
            filterCounts = countByFilter(games),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliothequeUiState(),
    )

    fun onFilterSelected(filter: BacklogFilter) {
        selectedFilter.value = filter
    }
}
