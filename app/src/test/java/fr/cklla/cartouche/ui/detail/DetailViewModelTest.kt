package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate
import fr.cklla.cartouche.domain.model.MAX_HOURS
import fr.cklla.cartouche.domain.model.MAX_NOTES_LENGTH
import fr.cklla.cartouche.domain.model.MAX_PLAYED_PLATFORMS
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun setUpGame(repository: GameRepository): String {
        val result = repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE),
        )
        return (result as Resource.Success).data
    }

    private fun viewModel(
        repository: GameRepository,
        gameId: String,
        igdbPlaytimeRepository: FakeIgdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
        igdbUpgradeStore: FakeIgdbUpgradeStore = FakeIgdbUpgradeStore(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to gameId)),
        gameRepository = repository,
        igdbPlaytimeRepository = igdbPlaytimeRepository,
        igdbUpgradeStore = igdbUpgradeStore,
    )

    private fun previewViewModel(
        repository: GameRepository,
        title: String = "Hollow Knight",
        igdbId: Long = 42,
        igdbPlaytimeRepository: FakeIgdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
        igdbUpgradeStore: FakeIgdbUpgradeStore = FakeIgdbUpgradeStore(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                CartoucheDestinations.DETAIL_APERCU_ARG_IGDB_ID to igdbId,
                CartoucheDestinations.DETAIL_APERCU_ARG_TITLE to title,
                CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM to "PC",
                CartoucheDestinations.DETAIL_APERCU_ARG_GENRE to "Metroidvania",
                CartoucheDestinations.DETAIL_APERCU_ARG_YEAR to "2017",
                CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL to "",
            ),
        ),
        gameRepository = repository,
        igdbPlaytimeRepository = igdbPlaytimeRepository,
        igdbUpgradeStore = igdbUpgradeStore,
    )

    @Test
    fun `changer le statut met a jour le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(GameStatus.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(GameStatus.TERMINE, viewModel.uiState.value.game?.status)
        collectorJob.cancel()
    }

    @Test
    fun `les notes sont coupees a la borne du serveur`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotesChanged("n".repeat(MAX_NOTES_LENGTH + 100))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MAX_NOTES_LENGTH, viewModel.uiState.value.game?.notes?.length)
        assertEquals(MAX_NOTES_LENGTH, repository.observeGame(gameId).first()?.notes?.length)
        collectorJob.cancel()
    }

    @Test
    fun `le temps de jeu ne depasse jamais la borne du serveur`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(MAX_HOURS + 500)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MAX_HOURS, viewModel.uiState.value.game?.userPlaytimeHours)

        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MAX_HOURS, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `on ne peut pas cocher plus de plateformes jouees que la borne du serveur`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        repeat(MAX_PLAYED_PLATFORMS + 3) { viewModel.onPlayedPlatformToggled("P$it") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MAX_PLAYED_PLATFORMS, viewModel.uiState.value.game?.playedPlatforms?.size)
        // Décocher reste possible une fois la borne atteinte.
        viewModel.onPlayedPlatformToggled("P0")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MAX_PLAYED_PLATFORMS - 1, viewModel.uiState.value.game?.playedPlatforms?.size)
        collectorJob.cancel()
    }

    @Test
    fun `le temps de jeu ne descend jamais sous zero`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursDecrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `incrementer puis decrementer le temps de jeu revient a la valeur initiale`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursDecrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `saisir manuellement le temps de jeu met a jour le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(380)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(380, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `saisir manuellement puis incrementer part bien de la valeur saisie`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(380)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(381, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `saisir manuellement une valeur negative est ramenee a zero`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(-5)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `noter et modifier les notes libres met a jour le jeu`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRatingSelected(4)
        viewModel.onNotesChanged("Super jeu")
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertEquals(4, game?.rating)
        assertEquals("Super jeu", game?.notes)
        collectorJob.cancel()
    }

    @Test
    fun `recliquer sur l'etoile de la note actuelle efface la note`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRatingSelected(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.game?.rating)

        viewModel.onRatingSelected(null)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.game?.rating)
        collectorJob.cancel()
    }

    @Test
    fun `cocher une plateforme jouee ajoute une plateforme sans retirer les autres`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(title = "Trails in the Sky", platform = "PC/PS5/Switch", genre = "RPG", status = GameStatus.EN_COURS),
        )
        val gameId = (result as Resource.Success).data
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlayedPlatformToggled("Switch")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPlayedPlatformToggled("PC")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Switch", "PC"), viewModel.uiState.value.game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `decocher une plateforme jouee la retire de la selection`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Trails in the Sky",
                platform = "PC/PS5/Switch",
                genre = "RPG",
                status = GameStatus.EN_COURS,
                playedPlatforms = setOf("Switch"),
            ),
        )
        val gameId = (result as Resource.Success).data
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlayedPlatformToggled("Switch")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `retirer le jeu du backlog fait disparaitre le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveGame()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.game)
        assertEquals(false, viewModel.uiState.value.isLoading)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir la fiche declenche une recherche IGDB quand aucun temps estime n'est en cache`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 7,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 20, normallyHours = 25, completelyHours = 40),
                platform = null,
            ),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        val game = viewModel.uiState.value.game
        assertEquals(20, game?.estimatedPlaytimeHastilyHours)
        assertEquals(25, game?.estimatedPlaytimeNormallyHours)
        assertEquals(40, game?.estimatedPlaytimeCompletelyHours)
        assertTrue(game?.igdbLookupAttempted == true)
        collectorJob.cancel()
    }

    @Test
    fun `un temps estime partiel (IGDB) laisse les champs non renseignes a null`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 7,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = null, normallyHours = 12, completelyHours = null),
                platform = null,
            ),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertNull(game?.estimatedPlaytimeHastilyHours)
        assertEquals(12, game?.estimatedPlaytimeNormallyHours)
        assertNull(game?.estimatedPlaytimeCompletelyHours)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir la fiche ne redemande pas IGDB si une tentative a deja ete enregistree`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Hades",
                platform = "PC",
                genre = "Roguelike",
                status = GameStatus.A_FAIRE,
                estimatedPlaytimeNormallyHours = 20,
                igdbId = 7,
                igdbLookupAttempted = true,
            ),
        )
        val gameId = (result as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 7,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 1, normallyHours = 999, completelyHours = 1),
                platform = null,
            ),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, igdbRepository.callCount)
        assertEquals(20, viewModel.uiState.value.game?.estimatedPlaytimeNormallyHours)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir la fiche relance IGDB si un temps de jeu partiel est en cache mais qu'aucune tentative n'a ete enregistree`() = runTest {
        // Cas réel ayant motivé l'introduction d'`igdbLookupAttempted` : un jeu ajouté avant la
        // correction de plateforme peut déjà avoir un temps de jeu partiel en cache sans qu'IGDB
        // ait jamais été interrogé pour sa plateforme — se fier au temps de jeu comme signal de
        // déclenchement (ancien comportement) bloquait alors cette correction pour toujours.
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Trails in the Sky 1st Chapter",
                platform = "PC/PlayStation 5/Nintendo Switch",
                genre = "RPG",
                status = GameStatus.TERMINE,
                estimatedPlaytimeNormallyHours = 57,
                igdbLookupAttempted = false,
            ),
        )
        val gameId = (result as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(igdbId = 7, playtimeEstimate = null, platform = "Nintendo Switch/PC/PlayStation 5"),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        val game = viewModel.uiState.value.game
        assertEquals("Nintendo Switch/PC/PlayStation 5", game?.platform)
        assertEquals(57, game?.estimatedPlaytimeNormallyHours)
        assertTrue(game?.igdbLookupAttempted == true)
        collectorJob.cancel()
    }

    @Test
    fun `un echec IGDB laisse les temps de jeu estimes a null mais enregistre quand meme la tentative`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(result = null)
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        val game = viewModel.uiState.value.game
        assertNull(game?.estimatedPlaytimeHastilyHours)
        assertNull(game?.estimatedPlaytimeNormallyHours)
        assertNull(game?.estimatedPlaytimeCompletelyHours)
        assertTrue(game?.igdbLookupAttempted == true)
        collectorJob.cancel()
    }

    @Test
    fun `un jeu qui connait son id IGDB le transmet pour une lecture directe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = (repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE, igdbId = 113112L),
        ) as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(result = IgdbGameMatch(igdbId = 113112L))
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(113112L, igdbRepository.lastIgdbId)
        collectorJob.cancel()
    }

    @Test
    fun `un jeu sans id IGDB deja passe par l'ancienne correspondance est relance une seule fois`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = (repository.addGame(
            Game(
                title = "Hades",
                platform = "PC",
                genre = "Roguelike",
                status = GameStatus.A_FAIRE,
                coverUrl = "https://media.rawg.io/media/games/hades.jpg",
                igdbLookupAttempted = true,
            ),
        ) as Resource.Success).data
        val store = FakeIgdbUpgradeStore()
        val match = IgdbGameMatch(igdbId = 113112L, coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co1.jpg")

        // Première ouverture : la remise à niveau a lieu, le jeu reçoit son id et sa jaquette IGDB.
        val first = FakeIgdbPlaytimeRepository(result = match)
        val firstViewModel = viewModel(repository, gameId, first, store)
        val firstJob = launch { firstViewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, first.callCount)
        assertEquals(113112L, firstViewModel.uiState.value.game?.igdbId)
        assertEquals(match.coverUrl, firstViewModel.uiState.value.game?.coverUrl)
        assertTrue(gameId in store.doneIds)
        firstJob.cancel()

        // Seconde ouverture avec le même magasin : plus aucune relance.
        val second = FakeIgdbPlaytimeRepository(result = match)
        val secondViewModel = viewModel(repository, gameId, second, store)
        val secondJob = launch { secondViewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, second.callCount)
        secondJob.cancel()
    }

    @Test
    fun `un jeu sans id IGDB introuvable n'est pas relance a chaque ouverture`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = (repository.addGame(
            Game(
                title = "Jeu inconnu",
                platform = "PC",
                genre = "",
                status = GameStatus.A_FAIRE,
                igdbLookupAttempted = true,
            ),
        ) as Resource.Success).data
        val store = FakeIgdbUpgradeStore()

        val first = FakeIgdbPlaytimeRepository(result = null)
        val firstJob = launch { viewModel(repository, gameId, first, store).uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        firstJob.cancel()
        val second = FakeIgdbPlaytimeRepository(result = null)
        val secondJob = launch { viewModel(repository, gameId, second, store).uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        secondJob.cancel()

        assertEquals(1, first.callCount)
        assertEquals(0, second.callCount)
    }

    @Test
    fun `un echec de lecture IGDB ne consomme pas la tentative, la suivante aboutit`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = (repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE, igdbId = 113112L),
        ) as Resource.Success).data
        val store = FakeIgdbUpgradeStore()

        // Hors ligne : rien n'est enregistré, ni drapeau, ni mémoire de remise à niveau.
        val offline = FakeIgdbPlaytimeRepository(fails = true)
        val offlineViewModel = viewModel(repository, gameId, offline, store)
        val offlineJob = launch { offlineViewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, offline.callCount)
        assertFalse(offlineViewModel.uiState.value.game?.igdbLookupAttempted == true)
        assertFalse(gameId in store.doneIds)
        offlineJob.cancel()

        // Retour du réseau : la fiche relit le jeu et reçoit ses temps.
        val online = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 113112L,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 20, normallyHours = 25, completelyHours = 40),
            ),
        )
        val onlineViewModel = viewModel(repository, gameId, online, store)
        val onlineJob = launch { onlineViewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, online.callCount)
        assertEquals(25, onlineViewModel.uiState.value.game?.estimatedPlaytimeNormallyHours)
        assertTrue(onlineViewModel.uiState.value.game?.igdbLookupAttempted == true)
        onlineJob.cancel()
    }

    @Test
    fun `un jeu ajoute depuis l'apercu pendant une panne garde sa tentative pour plus tard`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val igdbRepository = FakeIgdbPlaytimeRepository(fails = true)
        val viewModel = previewViewModel(repository, title = "Hollow Knight", igdbPlaytimeRepository = igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddGame()
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertTrue(game?.id?.isNotEmpty() == true)
        assertFalse(game?.igdbLookupAttempted == true)
        collectorJob.cancel()
    }

    @Test
    fun `un jeu qui a son id IGDB et une tentative enregistree n'est pas relance`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = (repository.addGame(
            Game(
                title = "Hades",
                platform = "PC",
                genre = "Roguelike",
                status = GameStatus.A_FAIRE,
                igdbId = 113112L,
                igdbLookupAttempted = true,
            ),
        ) as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(result = IgdbGameMatch(igdbId = 113112L))
        val job = launch { viewModel(repository, gameId, igdbRepository).uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, igdbRepository.callCount)
        job.cancel()
    }

    @Test
    fun `une fiche ouverte depuis la recherche demarre en apercu, hors backlog`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Hollow Knight", state.game?.title)
        assertEquals("", state.game?.id)
        assertFalse(state.isInBacklog)
        assertFalse(state.isLoading)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter depuis l'apercu persiste le jeu et bascule la fiche dans le backlog`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddGame()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInBacklog)
        assertTrue(state.game?.id?.isNotEmpty() == true)
        assertEquals(1, dao.getAllIds().size)
        collectorJob.cancel()
    }

    @Test
    fun `une modification en apercu met a jour la copie locale sans rien persister`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotesChanged("À essayer")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("À essayer", viewModel.uiState.value.game?.notes)
        assertEquals(0, dao.getAllIds().size)
        collectorJob.cancel()
    }

    @Test
    fun `l'apercu declenche aussi une recherche IGDB`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 7,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 10, normallyHours = 27, completelyHours = 60),
                platform = null,
            ),
        )
        val viewModel = previewViewModel(repository, title = "Hollow Knight", igdbPlaytimeRepository = igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        assertEquals(27, viewModel.uiState.value.game?.estimatedPlaytimeNormallyHours)
        assertEquals(0, dao.getAllIds().size)
        collectorJob.cancel()
    }

    @Test
    fun `une correspondance IGDB sans plateforme laisse platform inchange`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository) // platform = "PC"
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(igdbId = 7, playtimeEstimate = null, platform = null),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("PC", viewModel.uiState.value.game?.platform)
        collectorJob.cancel()
    }

    @Test
    fun `une plateforme IGDB remplace platform quand aucune plateforme jouee n'est cochee`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository) // platform = "PC", playedPlatforms vide
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(igdbId = 7, playtimeEstimate = null, platform = "PC (Microsoft Windows)"),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("PC (Microsoft Windows)", viewModel.uiState.value.game?.platform)
        collectorJob.cancel()
    }

    @Test
    fun `une plateforme jouee cochee est realignee sur la casse IGDB quand platform est remplace`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Trails in the Sky",
                platform = "PC/PS5/Switch",
                genre = "RPG",
                status = GameStatus.EN_COURS,
                playedPlatforms = setOf("Switch"),
            ),
        )
        val gameId = (result as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(igdbId = 7, playtimeEstimate = null, platform = "PC/PlayStation 5/switch"),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertEquals("PC/PlayStation 5/switch", game?.platform)
        assertEquals(setOf("switch"), game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `platform unique est corrige par un rapprochement certain (Switch vers Switch 2)`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Kirby Air Riders",
                platform = "Switch",
                genre = "Course",
                status = GameStatus.A_FAIRE,
                playedPlatforms = setOf("Switch"),
            ),
        )
        val gameId = (result as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(igdbId = 7, playtimeEstimate = null, platform = "Nintendo Switch 2"),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertEquals("Nintendo Switch 2", game?.platform)
        assertEquals(setOf("Nintendo Switch 2"), game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `temps de jeu et plateforme sont appliques ensemble depuis la meme correspondance IGDB`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository) // platform = "PC"
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbGameMatch(
                igdbId = 7,
                playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 8, normallyHours = 15, completelyHours = 30),
                platform = "PC (Microsoft Windows)",
            ),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertEquals(15, game?.estimatedPlaytimeNormallyHours)
        assertEquals("PC (Microsoft Windows)", game?.platform)
        collectorJob.cancel()
    }
}
