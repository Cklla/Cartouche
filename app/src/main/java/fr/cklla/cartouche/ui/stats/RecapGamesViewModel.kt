package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.bibliotheque.filterGames
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Liste des jeux Terminé/Abandonné d'une année, ouverte depuis `RecapScreen` en tapant sur l'un
 * des deux compteurs. [filter]/[year] reçus via la route de navigation (voir
 * `CartoucheDestinations.recapGamesRoute`) ; le filtrage lui-même réutilise `filterGames` de la
 * Bibliothèque plutôt qu'une nouvelle logique — même résultat que d'ouvrir l'onglet Terminé/
 * Abandonné de la Bibliothèque sur cette année-là.
 */
@HiltViewModel
class RecapGamesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameRepository: GameRepository,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[CartoucheDestinations.RECAP_ARG_YEAR])
    val filter: BacklogFilter =
        BacklogFilter.valueOf(checkNotNull(savedStateHandle[CartoucheDestinations.RECAP_GAMES_ARG_FILTER]))

    val games: StateFlow<List<Game>> = gameRepository.observeGames()
        .map { games -> filterGames(games, filter, year) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
