package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.FakeIgdbApi
import fr.cklla.cartouche.data.remote.igdb.FakeIgdbTokenSource
import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples
import fr.cklla.cartouche.data.remote.igdb.igdbGame
import fr.cklla.cartouche.domain.model.Resource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSearchRepositoryImplTest {

    private fun repository(api: FakeIgdbApi, token: String? = "jeton") =
        GameSearchRepositoryImpl(IgdbGateway(api, FakeIgdbTokenSource(token)))

    @Test
    fun `une recherche vide ne fait aucun appel reseau et renvoie une liste vide`() = runTest {
        val api = FakeIgdbApi(catalog = emptyList())

        val result = repository(api).searchGames(" ")

        assertEquals(Resource.Success(emptyList<Nothing>()), result)
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun `une recherche reussie convertit les resultats IGDB`() = runTest {
        val hades = igdbGame(113112, "Hades", platforms = listOf("PC (Microsoft Windows)", "Nintendo Switch"), genre = "Roguelike", cover = "co2ztu")
        val api = FakeIgdbApi(catalog = listOf(hades), searchResults = listOf(hades.id))

        val result = repository(api).searchGames("hades") as Resource.Success

        val first = result.data.single()
        assertEquals(113112L, first.igdbId)
        assertEquals("Hades", first.title)
        assertEquals("Nintendo Switch/PC", first.platform)
        assertEquals("Roguelike", first.genre)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co2ztu.jpg", first.coverUrl)
    }

    @Test
    fun `la requete envoyee est nettoyee des espaces et echappee`() = runTest {
        val api = FakeIgdbApi(catalog = emptyList())

        repository(api).searchGames("  Baldur's \"Gate\" ")

        assertTrue(api.queries.first().startsWith("search \"Baldur's \\\"Gate\\\"\";"))
    }

    @Test
    fun `la recherche fusionne les plateformes d'une edition dans son jeu`() = runTest {
        val api = FakeIgdbApi(
            catalog = listOf(IgdbSamples.xenoblade, IgdbSamples.xenobladeDefinitive, IgdbSamples.xenobladeSwitch2),
            searchResults = listOf(IgdbSamples.xenoblade.id, IgdbSamples.xenobladeDefinitive.id),
        )

        val data = (repository(api).searchGames("xenoblade chronicles") as Resource.Success).data

        // Jeu de base et jeu étendu : deux fiches ; l'édition Switch 2 rejoint le jeu étendu.
        assertEquals(listOf("Wii/Wii U", "Nintendo Switch/Nintendo Switch 2"), data.map { it.platform })
    }

    @Test
    fun `un echec reseau renvoie une erreur structuree`() = runTest {
        val api = FakeIgdbApi(catalog = emptyList()).apply { failure = IOException("hors ligne") }

        val result = repository(api).searchGames("hades")

        assertTrue(result is Resource.Error)
        assertTrue((result as Resource.Error).cause is IOException)
    }

    @Test
    fun `l'impossibilite d'obtenir un jeton renvoie une erreur structuree sans requete`() = runTest {
        val api = FakeIgdbApi(catalog = emptyList())

        val result = repository(api, token = null).searchGames("hades")

        assertTrue(result is Resource.Error)
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun `une recherche annulee n'est pas une erreur`() = runTest {
        val api = FakeIgdbApi(catalog = emptyList()).apply { failure = CancellationException("remplacee par la frappe suivante") }

        val outcome = runCatching { repository(api).searchGames("hades") }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertNull(outcome.getOrNull())
    }
}
