package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Slides à afficher. [isLoading] vaut `true` tant que le backlog n'a pas répondu : l'écran attend
 * la première liste réelle avant de créer son pager, pour qu'un état de page restauré (rotation,
 * retour depuis une fiche) ne soit pas ramené à 0 par une liste de slides encore vide.
 */
data class RecapStoryUiState(
    val isLoading: Boolean = true,
    val slides: List<RecapSlide> = emptyList(),
)

/**
 * Récap en images d'une année. L'année vient de la route (comme `RecapViewModel`) ; les slides sont
 * recalculées à chaque changement du backlog (note, statut, année de fin), jamais figées à
 * l'ouverture, donc une modification faite depuis une fiche se répercute au retour.
 *
 * Le fuseau passé aux calculs est celui de l'appareil, lu à ce point d'usage : c'est aussi celui que
 * `completedYear` utilise pour classer les jeux dans l'année.
 */
@HiltViewModel
class RecapStoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameRepository: GameRepository,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[CartoucheDestinations.RECAP_ARG_YEAR])

    val uiState: StateFlow<RecapStoryUiState> = gameRepository.observeGames()
        .map { games ->
            RecapStoryUiState(isLoading = false, slides = buildRecapSlides(games, year, ZoneId.systemDefault()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecapStoryUiState(),
        )
}
