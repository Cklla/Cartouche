package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.domain.model.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSearchRepositoryImplTest {

    @Test
    fun `une recherche vide ne fait aucun appel reseau et renvoie une liste vide`() = runTest {
        val api = FakeRawgApi().apply { shouldThrow = true } // prouve qu'il n'est pas appelé
        val repository = GameSearchRepositoryImpl(api)

        val result = repository.searchGames(" ")

        assertEquals(Resource.Success(emptyList<Nothing>()), result)
    }

    @Test
    fun `une recherche reussie convertit les resultats RAWG`() = runTest {
        val api = FakeRawgApi().apply {
            results = listOf(RawgGameDto(id = 1, name = "Hades"))
        }
        val repository = GameSearchRepositoryImpl(api)

        val result = repository.searchGames("hades") as Resource.Success

        assertEquals(1, result.data.size)
        assertEquals("Hades", result.data.first().title)
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val api = FakeRawgApi().apply { shouldThrow = true }
        val repository = GameSearchRepositoryImpl(api)

        val result = repository.searchGames("hades")

        assertTrue(result is Resource.Error)
    }
}
