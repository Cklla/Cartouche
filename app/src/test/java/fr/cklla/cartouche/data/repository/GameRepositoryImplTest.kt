package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameRepositoryImplTest {

    private lateinit var dao: FakeGameDao
    private lateinit var repository: GameRepository

    private val hades = Game(
        title = "Hades",
        platform = "Switch",
        genre = "Roguelike",
        status = GameStatus.A_FAIRE,
    )

    @Before
    fun setUp() {
        dao = FakeGameDao()
        repository = GameRepositoryImpl(dao)
    }

    @Test
    fun `addGame rend le jeu visible via observeGames`() = runTest {
        val result = repository.addGame(hades)

        assertTrue(result is Resource.Success)
        val games = repository.observeGames().first()
        assertEquals(1, games.size)
        assertEquals("Hades", games.first().title)
    }

    @Test
    fun `updateGame modifie le statut du jeu observe`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        val enCours = hades.copy(id = addedId, status = GameStatus.EN_COURS)

        repository.updateGame(enCours)

        val games = repository.observeGames().first()
        assertEquals(GameStatus.EN_COURS, games.first().status)
    }

    @Test
    fun `deleteGame retire le jeu du backlog`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        repository.deleteGame(addedId)

        assertTrue(repository.observeGames().first().isEmpty())
    }

    @Test
    fun `addGame renvoie une erreur structuree si le dao echoue`() = runTest {
        dao.shouldThrowOnInsert = true

        val result = repository.addGame(hades)

        assertTrue(result is Resource.Error)
    }
}
