package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.model.realignPlayedPlatformsOrNull
import fr.cklla.cartouche.domain.model.toGame
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le jeu affiché est chargé une fois depuis le repository, puis conservé dans
 * une copie de travail locale ([workingGame]) que chaque action met à jour
 * *avant* de persister (fire-and-forget) via le repository.
 *
 * Sans cette copie locale, deux actions déclenchées coup sur coup (ex. taper
 * plusieurs caractères dans les notes, ou noter puis changer le statut) liraient
 * toutes les deux le même jeu "avant écriture" — Room et Firestore étant
 * asynchrones, la première modification serait alors écrasée par la
 * seconde à l'aller-retour suivant. Le repository reste la seule source de
 * vérité pour la *persistance* ; [workingGame] n'est qu'un cache d'édition
 * pour cet écran.
 *
 * Deux façons d'arriver sur cette fiche : depuis la Bibliothèque, avec l'id d'un jeu déjà dans le
 * backlog ([gameId] renseigné) ; ou directement depuis un résultat de Recherche pas encore ajouté,
 * auquel cas la fiche RAWG transite par les autres arguments de route ([previewResult]) et
 * [workingGame] démarre avec un id vide. Tant que l'id est vide, la fiche est en aperçu
 * (`DetailUiState.isInBacklog` vaut `false`) et aucune action d'édition n'est persistée — voir
 * [applyEdit] — jusqu'à ce que [onAddGame] l'ajoute réellement au backlog.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val igdbPlaytimeRepository: IgdbPlaytimeRepository,
) : ViewModel() {

    private val gameId: String? = savedStateHandle[CartoucheDestinations.DETAIL_ARG_GAME_ID]
    private val previewResult: GameSearchResult? =
        if (gameId != null) null else savedStateHandle.toPreviewResult()

    private val workingGame = MutableStateFlow(previewResult?.toGame())
    private val isLoading = MutableStateFlow(gameId != null)

    init {
        val id = gameId
        if (id != null) {
            viewModelScope.launch {
                val game = gameRepository.observeGame(id).first()
                workingGame.value = game
                isLoading.value = false
                game?.let(::fetchEstimatedPlaytimeIfMissing)
            }
        } else {
            workingGame.value?.let(::fetchEstimatedPlaytimeIfMissing)
        }
    }

    // Lookup IGDB déclenché une seule fois, seulement si aucun des trois temps de jeu estimés
    // n'est déjà en cache (voir `Game.estimatedPlaytime*Hours`) — que la fiche soit déjà dans le
    // backlog ou encore en aperçu, jamais pendant la recherche RAWG elle-même.
    private fun fetchEstimatedPlaytimeIfMissing(game: Game) {
        if (game.estimatedPlaytimeHastilyHours == null &&
            game.estimatedPlaytimeNormallyHours == null && game.estimatedPlaytimeCompletelyHours == null
        ) {
            fetchEstimatedPlaytime(game)
        }
    }

    private fun fetchEstimatedPlaytime(game: Game) {
        viewModelScope.launch {
            val match = igdbPlaytimeRepository.findEstimatedPlaytime(
                title = game.title,
                releaseYear = game.releaseYear,
                rawgId = game.rawgId,
            ) ?: return@launch
            // Le jeu affiché a pu changer entretemps (retiré du backlog) : `applyEdit` gère déjà
            // ce cas (no-op si `workingGame` est `null`), donc pas de vérification supplémentaire ici.
            applyEdit { applyIgdbMatch(it, match) }
        }
    }

    /**
     * Applique un [IgdbGameMatch] résolu à [game] : temps de jeu estimé si IGDB en a trouvé, et
     * correction de `platform` si IGDB a une liste de plateformes et que ce remplacement ne casse
     * aucune entrée déjà cochée de `playedPlatforms` (voir `realignPlayedPlatformsOrNull` pour le
     * raisonnement détaillé — notamment le cas Switch/Switch 2 qui motive cette prudence).
     */
    private fun applyIgdbMatch(game: Game, match: IgdbGameMatch): Game {
        val withPlaytime = match.playtimeEstimate?.let { estimate ->
            game.copy(
                estimatedPlaytimeHastilyHours = estimate.hastilyHours,
                estimatedPlaytimeNormallyHours = estimate.normallyHours,
                estimatedPlaytimeCompletelyHours = estimate.completelyHours,
            )
        } ?: game

        val newPlatform = match.platform ?: return withPlaytime
        val realignedPlayedPlatforms = realignPlayedPlatformsOrNull(withPlaytime.playedPlatforms, newPlatform)
            ?: return withPlaytime
        return withPlaytime.copy(platform = newPlatform, playedPlatforms = realignedPlayedPlatforms)
    }

    val uiState: StateFlow<DetailUiState> = combine(isLoading, workingGame) { loading, game ->
        DetailUiState(isLoading = loading, game = game)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = isLoading.value, game = workingGame.value),
    )

    fun onStatusSelected(status: GameStatus) = applyEdit { it.copy(status = status) }

    // `rating = null` correspond à "aucune note" : cliquer sur l'étoile qui représente déjà la
    // note actuelle (voir `RatingSection`) doit pouvoir revenir à cet état, pas seulement en
    // choisir une nouvelle.
    fun onRatingSelected(rating: Int?) = applyEdit { it.copy(rating = rating) }

    fun onHoursIncrement() = applyEdit { it.copy(userPlaytimeHours = it.userPlaytimeHours + 1) }

    fun onHoursDecrement() =
        applyEdit { it.copy(userPlaytimeHours = (it.userPlaytimeHours - 1).coerceAtLeast(0)) }

    // Saisie manuelle (voir `HoursValueField`) : permet de renseigner directement un temps de jeu
    // élevé sans passer par des dizaines de clics sur le bouton "+" (ex. 380h sur un JRPG).
    fun onHoursSet(hours: Int) = applyEdit { it.copy(userPlaytimeHours = hours.coerceAtLeast(0)) }

    fun onNotesChanged(notes: String) = applyEdit { it.copy(notes = notes) }

    // Cases à cocher "joué sur" (voir `PlayedPlatformsSection`) : plusieurs plateformes peuvent
    // être cochées à la fois (jeu fait pour partie sur PC, pour partie sur PS5 par exemple).
    fun onPlayedPlatformToggled(platform: String) = applyEdit {
        val updated = if (platform in it.playedPlatforms) it.playedPlatforms - platform else it.playedPlatforms + platform
        it.copy(playedPlatforms = updated)
    }

    /**
     * Ajoute la fiche en aperçu au backlog. La fiche ne navigue nulle part : `workingGame` reçoit
     * l'id fraîchement généré, ce qui fait basculer `isInBacklog` à `true` et affiche directement
     * statut/note perso/temps de jeu/notes libres sur le même écran.
     */
    fun onAddGame() {
        val current = workingGame.value ?: return
        if (current.id.isNotEmpty()) return
        viewModelScope.launch {
            val result = gameRepository.addGame(current)
            if (result is Resource.Success) {
                workingGame.value = current.copy(id = result.data)
            }
        }
    }

    fun onRemoveGame() {
        val id = gameId ?: return
        // Retrait optimiste : l'écran n'attend pas l'aller-retour Room pour
        // considérer le jeu comme supprimé (voir `DetailScreen`, qui revient en
        // arrière dès que `game` devient `null`).
        workingGame.value = null
        viewModelScope.launch { gameRepository.deleteGame(id) }
    }

    private fun applyEdit(transform: (Game) -> Game) {
        val updated = workingGame.value?.let(transform) ?: return
        workingGame.value = updated
        // Pas encore ajouté au backlog : rien à persister, seule la copie de travail locale
        // change (voir `onAddGame`, qui écrit pour la première fois).
        if (updated.id.isEmpty()) return
        viewModelScope.launch { gameRepository.updateGame(updated) }
    }

    private fun SavedStateHandle.toPreviewResult(): GameSearchResult? {
        val rawgId = get<Long>(CartoucheDestinations.DETAIL_APERCU_ARG_RAWG_ID)?.takeIf { it > 0 } ?: return null
        return GameSearchResult(
            rawgId = rawgId,
            title = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_TITLE).orEmpty(),
            platform = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM).orEmpty(),
            genre = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_GENRE).orEmpty(),
            year = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_YEAR).orEmpty(),
            coverUrl = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL)?.takeIf { it.isNotEmpty() },
        )
    }
}
