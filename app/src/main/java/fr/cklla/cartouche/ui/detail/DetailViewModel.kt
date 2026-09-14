package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le jeu affiché est chargé une fois depuis le repository, puis conservé dans
 * une copie de travail locale ([workingGame]) que chaque action met à jour
 * *avant* de persister (fire-and-forget) via le repository.
 *
 * Sans cette copie locale, deux actions déclenchées coup sur coup (ex. taper
 * plusieurs caractères dans les notes, ou noter puis changer le statut) liraient
 * toutes les deux le même jeu "avant écriture" — Room (et plus tard Firestore)
 * étant asynchrones, la première modification serait alors écrasée par la
 * seconde à l'aller-retour suivant. Le repository reste la seule source de
 * vérité pour la *persistance* ; [workingGame] n'est qu'un cache d'édition
 * pour cet écran.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val igdbPlaytimeRepository: IgdbPlaytimeRepository,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle[CartoucheDestinations.DETAIL_ARG_GAME_ID])

    private val workingGame = MutableStateFlow<Game?>(null)
    private val isLoading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            val game = gameRepository.observeGame(gameId).first()
            workingGame.value = game
            isLoading.value = false

            // Lookup IGDB déclenché une seule fois, seulement à l'ouverture de la fiche et
            // seulement si aucun des trois temps de jeu estimés n'est déjà en cache (voir
            // `Game.estimatedPlaytime*Hours`) — jamais pendant la recherche RAWG ni sur la liste
            // du backlog, pour éviter des appels inutiles.
            if (game != null && game.estimatedPlaytimeHastilyHours == null &&
                game.estimatedPlaytimeNormallyHours == null && game.estimatedPlaytimeCompletelyHours == null
            ) {
                fetchEstimatedPlaytime(game)
            }
        }
    }

    private fun fetchEstimatedPlaytime(game: Game) {
        viewModelScope.launch {
            val estimate = igdbPlaytimeRepository.findEstimatedPlaytime(game.title) ?: return@launch
            // Le jeu affiché a pu changer entretemps (retiré du backlog) : `applyEdit` gère déjà
            // ce cas (no-op si `workingGame` est `null`), donc pas de vérification supplémentaire ici.
            applyEdit {
                it.copy(
                    estimatedPlaytimeHastilyHours = estimate.hastilyHours,
                    estimatedPlaytimeNormallyHours = estimate.normallyHours,
                    estimatedPlaytimeCompletelyHours = estimate.completelyHours,
                )
            }
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(isLoading, workingGame) { loading, game ->
        DetailUiState(isLoading = loading, game = game)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    fun onStatusSelected(status: GameStatus) = applyEdit { it.copy(status = status) }

    fun onRatingSelected(rating: Int) = applyEdit { it.copy(rating = rating) }

    fun onHoursIncrement() = applyEdit { it.copy(userPlaytimeHours = it.userPlaytimeHours + 1) }

    fun onHoursDecrement() =
        applyEdit { it.copy(userPlaytimeHours = (it.userPlaytimeHours - 1).coerceAtLeast(0)) }

    fun onNotesChanged(notes: String) = applyEdit { it.copy(notes = notes) }

    fun onRemoveGame() {
        // Retrait optimiste : l'écran n'attend pas l'aller-retour Room pour
        // considérer le jeu comme supprimé (voir `DetailScreen`, qui revient en
        // arrière dès que `game` devient `null`).
        workingGame.value = null
        viewModelScope.launch { gameRepository.deleteGame(gameId) }
    }

    private fun applyEdit(transform: (Game) -> Game) {
        val updated = workingGame.value?.let(transform) ?: return
        workingGame.value = updated
        viewModelScope.launch { gameRepository.updateGame(updated) }
    }
}
