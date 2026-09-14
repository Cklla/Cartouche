package fr.cklla.cartouche.ui.recherche

import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.model.toGame
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.repository.GameSearchRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * La saisie ([query]) est exposée telle quelle (mise à jour immédiate, pour que
 * le champ de recherche et le passage "recherches populaires ↔ résultats"
 * réagissent sans délai), tandis que l'appel réseau réel est retardé
 * ([SEARCH_DEBOUNCE_MS]) et annulé/relancé à chaque frappe via `collectLatest`
 * — évite un appel RAWG par caractère tapé.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class RechercheViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameSearchRepository: GameSearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val searchResult = MutableStateFlow<Resource<List<GameSearchResult>>>(Resource.Success(emptyList()))

    init {
        viewModelScope.launch {
            query
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { currentQuery ->
                    if (currentQuery.isBlank()) {
                        isSearching.value = false
                        searchResult.value = Resource.Success(emptyList())
                        return@collectLatest
                    }
                    isSearching.value = true
                    searchResult.value = gameSearchRepository.searchGames(currentQuery)
                    isSearching.value = false
                }
        }
    }

    val uiState: StateFlow<RechercheUiState> = combine(
        query,
        isSearching,
        searchResult,
        gameRepository.observeGames(),
    ) { currentQuery, searching, result, backlog ->
        RechercheUiState(
            query = currentQuery,
            isSearching = searching,
            results = (result as? Resource.Success)?.data.orEmpty(),
            errorMessage = (result as? Resource.Error)?.message,
            backlogGameIdsByTitle = backlog.associate { normalizeTitle(it.title) to it.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RechercheUiState(),
    )

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
        // Passe isSearching à true dès la frappe (pas d'attente du debounce) pour éviter
        // un flash de l'état "aucun résultat"/erreur pendant les 300ms de délai.
        if (newQuery.isNotBlank()) isSearching.value = true
    }

    fun onSuggestionSelected(suggestion: String) = onQueryChanged(suggestion)

    fun onAddGame(result: GameSearchResult) {
        viewModelScope.launch { gameRepository.addGame(result.toGame()) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
