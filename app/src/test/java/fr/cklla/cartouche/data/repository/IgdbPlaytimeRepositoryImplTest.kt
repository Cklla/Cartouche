package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.FakeIgdbApi
import fr.cklla.cartouche.data.remote.igdb.FakeIgdbTokenSource
import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import fr.cklla.cartouche.data.remote.igdb.igdbGame
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbPlaytimeRepositoryImplTest {

    private val catalog = listOf(
        IgdbSamples.xenoblade, IgdbSamples.xenobladeDefinitive, IgdbSamples.xenobladeSwitch2,
        igdbGame(113112, "Hades", platforms = listOf("PC (Microsoft Windows)"), cover = "co2ztu", releaseDate = 1_600_000_000L),
    )

    private val ttb = IgdbTimeToBeatDto(hastilySeconds = 0, normallySeconds = 252_000, completelySeconds = 400_000)

    private fun repository(api: FakeIgdbApi, token: String? = "jeton") =
        IgdbPlaytimeRepositoryImpl(IgdbGateway(api, FakeIgdbTokenSource(token)))

    @Test
    fun `un id connu est lu directement, sans recherche par nom`() = runTest {
        val api = FakeIgdbApi(catalog, timeToBeat = ttb)

        val match = repository(api).findEstimatedPlaytime("Titre sans importance", 1999, IgdbSamples.XENOBLADE)!!

        assertEquals(IgdbSamples.XENOBLADE, match.igdbId)
        assertEquals("Nintendo Switch/Nintendo Switch 2/Wii/Wii U", match.platform)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/coc8ap.jpg", match.coverUrl)
        assertEquals(2010, match.releaseYear)
        // 0 seconde = « non disponible », jamais « 0 h ».
        assertNull(match.playtimeEstimate?.hastilyHours)
        assertEquals(70, match.playtimeEstimate?.normallyHours)
        assertEquals(111, match.playtimeEstimate?.completelyHours)
        assertTrue(api.queries.none { it.startsWith("search ") })
    }

    @Test
    fun `sans id, la correspondance se fait par nom et annee puis le jeu est lu par id`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L), timeToBeat = ttb)

        val match = repository(api).findEstimatedPlaytime("Hades", 2020, null)!!

        assertEquals(113112L, match.igdbId)
        assertEquals("PC", match.platform)
        assertTrue(api.queries.first().startsWith("search \"Hades\";"))
    }

    @Test
    fun `un titre avec suffixe d'annee est nettoye avant la recherche par nom`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        repository(api).findEstimatedPlaytime("Hades (2020)", 2020, null)

        assertTrue(api.queries.first().startsWith("search \"Hades\";"))
    }

    @Test
    fun `sans correspondance fiable par nom, le resultat est null`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        assertNull(repository(api).findEstimatedPlaytime("Un tout autre jeu", null, null))
    }

    @Test
    fun `un id hors borne est ignore et retombe sur la correspondance par nom`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        val match = repository(api).findEstimatedPlaytime("Hades", 2020, 10_000_000_000L)

        assertEquals(113112L, match?.igdbId)
    }

    @Test
    fun `un echec reseau ou sans jeton renvoie null sans exception`() = runTest {
        val failing = FakeIgdbApi(catalog).apply { failure = IOException("hors ligne") }

        assertNull(repository(failing).findEstimatedPlaytime("Hades", 2020, 113112L))
        assertNull(repository(FakeIgdbApi(catalog), token = null).findEstimatedPlaytime("Hades", 2020, 113112L))
    }

    @Test
    fun `un jeu sans duree ni jaquette est quand meme renvoye`() = runTest {
        val bare = igdbGame(5, "Sans rien", type = null, platforms = null)
        val api = FakeIgdbApi(listOf(bare))

        val match = repository(api).findEstimatedPlaytime("Sans rien", null, 5L)!!

        assertNull(match.platform)
        assertNull(match.coverUrl)
        assertNull(match.releaseYear)
        assertNull(match.playtimeEstimate)
    }
}
