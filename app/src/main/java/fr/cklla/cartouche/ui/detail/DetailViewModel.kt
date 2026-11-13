package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.MAX_HOURS
import fr.cklla.cartouche.domain.model.MAX_NOTES_LENGTH
import fr.cklla.cartouche.domain.model.MAX_PLAYED_PLATFORMS
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.model.applyIgdbEnrichment
import fr.cklla.cartouche.domain.model.igdbIdOrNull
import fr.cklla.cartouche.domain.model.toGame
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import fr.cklla.cartouche.domain.repository.IgdbUpgradeStore
import fr.cklla.cartouche.domain.util.selectablePlayedYears
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le jeu affiché est chargé une fois depuis le repository, puis conservé dans
 * une copie de travail locale ([workingGame]) que chaque action met à jour
 * *avant* de persister (fire-and-forget) via le repository.
 *
 * Sans cette copie locale, deux actions déclenchées coup sur coup (ex. taper
 * plusieurs caractères dans les notes, ou noter puis changer le statut) liraient
 * toutes les deux le même jeu "avant écriture" — Room et le serveur étant
 * asynchrones, la première modification serait alors écrasée par la
 * seconde à l'aller-retour suivant. Le repository reste la seule source de
 * vérité pour la *persistance* ; [workingGame] n'est qu'un cache d'édition
 * pour cet écran.
 *
 * Deux façons d'arriver sur cette fiche : depuis la Bibliothèque, avec l'id d'un jeu déjà dans le
 * backlog ([gameId] renseigné) ; ou directement depuis un résultat de Recherche pas encore ajouté,
 * auquel cas la fiche IGDB transite par les autres arguments de route ([previewResult]) et
 * [workingGame] démarre avec un id vide. Tant que l'id est vide, la fiche est en aperçu
 * (`DetailUiState.isInBacklog` vaut `false`) et aucune action d'édition n'est persistée — voir
 * [applyEdit] — jusqu'à ce que [onAddGame] l'ajoute réellement au backlog.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val igdbPlaytimeRepository: IgdbPlaytimeRepository,
    private val igdbUpgradeStore: IgdbUpgradeStore,
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

    // Lecture IGDB (règle d'enrichissement, voir `applyIgdbEnrichment`) déclenchée à l'ouverture de
    // la fiche, que le jeu soit déjà dans le backlog ou encore en aperçu, jamais pendant la
    // recherche elle-même. Deux cas : un jeu jamais passé par IGDB (`igdbLookupAttempted` faux), et
    // un jeu **sans id IGDB** déjà passé par l'ancienne correspondance, relancé une seule fois
    // (voir `IgdbUpgradeStore`) pour recevoir son id et sa jaquette IGDB.
    private fun fetchEstimatedPlaytimeIfMissing(game: Game) {
        val upgradeOnce = game.id.isNotEmpty() && game.igdbId == null && !igdbUpgradeStore.isDone(game.id)
        if (!game.igdbLookupAttempted || upgradeOnce) {
            fetchEstimatedPlaytime(game)
        }
    }

    private fun fetchEstimatedPlaytime(game: Game) {
        viewModelScope.launch {
            val match = igdbPlaytimeRepository.findEstimatedPlaytime(
                title = game.title,
                releaseYear = game.releaseYear,
                igdbId = igdbIdOrNull(game.igdbId),
            )
            // Un échec (réseau, jeu introuvable) compte aussi comme une tentative : pas de boucle.
            if (game.id.isNotEmpty()) igdbUpgradeStore.markDone(game.id)
            // Le jeu affiché a pu changer entretemps (retiré du backlog) : `applyEdit` gère déjà
            // ce cas (no-op si `workingGame` est `null`), donc pas de vérification supplémentaire ici.
            applyEdit { applyIgdbEnrichment(it, match) }
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(isLoading, workingGame) { loading, game ->
        DetailUiState(
            isLoading = loading,
            game = game,
            // L'instant présent et le fuseau sont lus ici, au point d'usage : la fonction de calcul
            // reste pure (voir `selectablePlayedYears`).
            statusYearChoices = game
                ?.takeIf { it.canEditStatusYear() }
                ?.let { selectablePlayedYears(it.releaseYear, System.currentTimeMillis(), ZoneId.systemDefault()) }
                .orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = isLoading.value, game = workingGame.value),
    )

    fun onStatusSelected(status: GameStatus) = applyEdit { it.copy(status = status) }

    /**
     * Classe le jeu dans l'année de fin ou d'abandon [year]. L'écriture et le calcul de
     * l'horodatage sont ceux du repository ([GameRepository.setStatusYear]) ; la copie de travail
     * reprend ensuite les dates réellement persistées, pour que l'affichage et les éditions
     * suivantes (note, statut) partent de la bonne valeur.
     */
    fun onStatusYearSelected(year: Int) {
        val game = workingGame.value?.takeIf { it.canEditStatusYear() } ?: return
        viewModelScope.launch {
            gameRepository.setStatusYear(game.id, year)
            refreshStatusDates(game.id)
        }
    }

    // `completedAt` / `abandonedAt` sont posés par le repository (transition de statut, choix d'une
    // année), jamais par la copie de travail : sans cette relecture, la fiche afficherait l'état
    // d'avant l'écriture (« année inconnue » juste après être passé à Terminé, ancienne année après
    // un choix). Ignorée si le statut persisté diffère déjà de la copie de travail (une autre
    // édition est en cours) : la relecture de cette édition-là prendra le relais.
    private suspend fun refreshStatusDates(id: String) {
        val persisted = gameRepository.observeGame(id).first() ?: return
        workingGame.update { current ->
            current?.takeIf { it.id == persisted.id && it.status == persisted.status }
                ?.copy(completedAt = persisted.completedAt, abandonedAt = persisted.abandonedAt)
                ?: current
        }
    }

    // `rating = null` correspond à "aucune note" : cliquer sur l'étoile qui représente déjà la
    // note actuelle (voir `RatingSection`) doit pouvoir revenir à cet état, pas seulement en
    // choisir une nouvelle.
    fun onRatingSelected(rating: Int?) = applyEdit { it.copy(rating = rating) }

    fun onHoursIncrement() =
        applyEdit { it.copy(userPlaytimeHours = (it.userPlaytimeHours + 1).coerceAtMost(MAX_HOURS)) }

    fun onHoursDecrement() =
        applyEdit { it.copy(userPlaytimeHours = (it.userPlaytimeHours - 1).coerceAtLeast(0)) }

    // Saisie manuelle (voir `HoursValueField`) : permet de renseigner directement un temps de jeu
    // élevé sans passer par des dizaines de clics sur le bouton "+" (ex. 380h sur un JRPG).
    fun onHoursSet(hours: Int) = applyEdit { it.copy(userPlaytimeHours = hours.coerceIn(0, MAX_HOURS)) }

    // Les notes sont bornées à la saisie (limite du serveur) : un texte plus long est coupé ici, sous les
    // yeux de l'utilisateur, plutôt qu'au moment de l'envoi où la fin disparaîtrait sans qu'il le voie.
    fun onNotesChanged(notes: String) = applyEdit { it.copy(notes = notes.take(MAX_NOTES_LENGTH)) }

    // Cases à cocher "joué sur" (voir `PlayedPlatformsSection`) : plusieurs plateformes peuvent
    // être cochées à la fois (jeu fait pour partie sur PC, pour partie sur PS5 par exemple).
    fun onPlayedPlatformToggled(platform: String) = applyEdit {
        val updated = when {
            platform in it.playedPlatforms -> it.playedPlatforms - platform
            it.playedPlatforms.size >= MAX_PLAYED_PLATFORMS -> it.playedPlatforms
            else -> it.playedPlatforms + platform
        }
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
                refreshStatusDates(result.data)
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
        viewModelScope.launch {
            gameRepository.updateGame(updated)
            refreshStatusDates(updated.id)
        }
    }

    private fun SavedStateHandle.toPreviewResult(): GameSearchResult? {
        // L'id vient d'un argument de route : on le valide comme toute entrée avant de s'en servir.
        val igdbId = igdbIdOrNull(get<Long>(CartoucheDestinations.DETAIL_APERCU_ARG_IGDB_ID)) ?: return null
        return GameSearchResult(
            igdbId = igdbId,
            title = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_TITLE).orEmpty(),
            platform = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM).orEmpty(),
            genre = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_GENRE).orEmpty(),
            year = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_YEAR).orEmpty(),
            coverUrl = get<String>(CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL)?.takeIf { it.isNotEmpty() },
        )
    }
}
