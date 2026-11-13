package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.FakeIgdbApi
import fr.cklla.cartouche.data.remote.igdb.FakeIgdbTokenSource
import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import fr.cklla.cartouche.data.remote.igdb.igdbGame
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbLookup
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbPlaytimeRepositoryImplTest {

    private val hades = igdbGame(
        113112, "Hades", platforms = listOf("PC (Microsoft Windows)"), cover = "co2ztu", releaseDate = 1_600_000_000L, // 2020
    )

    private val catalog = listOf(
        IgdbSamples.xenoblade, IgdbSamples.xenobladeDefinitive, IgdbSamples.xenobladeSwitch2, IgdbSamples.xenoblade3d, hades,
    )

    private val ttb = IgdbTimeToBeatDto(hastilySeconds = 0, normallySeconds = 252_000, completelySeconds = 400_000)

    private fun repository(api: FakeIgdbApi, token: String? = "jeton") =
        IgdbPlaytimeRepositoryImpl(IgdbGateway(api, FakeIgdbTokenSource(token)))

    private fun IgdbLookup.found(): IgdbGameMatch = (this as IgdbLookup.Found).match

    // --- Id connu ---

    @Test
    fun `un id connu est lu directement, sans recherche par nom`() = runTest {
        val api = FakeIgdbApi(catalog, timeToBeat = ttb)

        val match = repository(api).findEstimatedPlaytime("Titre sans importance", 1999, IgdbSamples.XENOBLADE).found()

        assertEquals(IgdbSamples.XENOBLADE, match.igdbId)
        assertEquals("New Nintendo 3DS/Nintendo 3DS/Wii/Wii U", match.platform)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/coc8ap.jpg", match.coverUrl)
        assertEquals(2010, match.releaseYear)
        // 0 seconde = « non disponible », jamais « 0 h ».
        assertNull(match.playtimeEstimate?.hastilyHours)
        assertEquals(70, match.playtimeEstimate?.normallyHours)
        assertEquals(111, match.playtimeEstimate?.completelyHours)
        assertTrue(api.queries.none { it.startsWith("search ") })
    }

    @Test
    fun `un id hors borne est ignore et retombe sur la correspondance par nom`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        val match = repository(api).findEstimatedPlaytime("Hades", 2020, 10_000_000_000L).found()

        assertEquals(113112L, match.igdbId)
    }

    // --- Rapprochement par nom : certain, ou sans id ni jaquette ---

    @Test
    fun `un rapprochement certain (titre identique et meme annee) pose l'id, la jaquette et l'annee`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L), timeToBeat = ttb)

        val match = repository(api).findEstimatedPlaytime("Hades", 2020, null).found()

        assertEquals(113112L, match.igdbId)
        assertEquals("PC", match.platform)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co2ztu.jpg", match.coverUrl)
        assertEquals(2020, match.releaseYear)
        assertTrue(api.queries.first().startsWith("search \"Hades\";"))
    }

    @Test
    fun `un rapprochement tombant sur un portage remonte au jeu de base`() = runTest {
        // IGDB ne renvoie que le portage 3DS : l'id retenu est celui du jeu de base.
        val api = FakeIgdbApi(catalog, searchResults = listOf(IgdbSamples.xenoblade3d.id))

        val match = repository(api).findEstimatedPlaytime("Xenoblade Chronicles", 2010, null).found()

        assertEquals(IgdbSamples.XENOBLADE, match.igdbId)
        assertTrue(match.platform!!.contains("Nintendo 3DS"))
    }

    @Test
    fun `une annee differente ne pose ni id ni jaquette mais garde plateformes et durees`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L), timeToBeat = ttb)

        val match = repository(api).findEstimatedPlaytime("Hades", 2019, null).found()

        assertNull(match.igdbId)
        assertNull(match.coverUrl)
        assertNull(match.releaseYear)
        assertEquals("PC", match.platform)
        assertEquals(70, match.playtimeEstimate?.normallyHours)
    }

    @Test
    fun `une annee enregistree inconnue ne pose pas d'id non plus`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        assertNull(repository(api).findEstimatedPlaytime("Hades", null, null).found().igdbId)
    }

    @Test
    fun `un titre avec suffixe d'annee est nettoye avant la recherche par nom`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        repository(api).findEstimatedPlaytime("Hades (2020)", 2020, null)

        assertTrue(api.queries.first().startsWith("search \"Hades\";"))
    }

    // --- Trois états ---

    @Test
    fun `sans correspondance fiable par nom, le jeu est introuvable (reponse definitive)`() = runTest {
        val api = FakeIgdbApi(catalog, searchResults = listOf(113112L))

        assertEquals(IgdbLookup.NotFound, repository(api).findEstimatedPlaytime("Un tout autre jeu", null, null))
    }

    @Test
    fun `un id inconnu d'IGDB est introuvable`() = runTest {
        assertEquals(IgdbLookup.NotFound, repository(FakeIgdbApi(emptyList())).findEstimatedPlaytime("Hades", 2020, 99L))
    }

    @Test
    fun `un echec reseau ou sans jeton est un echec, pas un jeu introuvable`() = runTest {
        val failing = FakeIgdbApi(catalog).apply { failure = IOException("hors ligne") }

        assertEquals(IgdbLookup.Failed, repository(failing).findEstimatedPlaytime("Hades", 2020, 113112L))
        assertEquals(IgdbLookup.Failed, repository(FakeIgdbApi(catalog), token = null).findEstimatedPlaytime("Hades", 2020, 113112L))
        assertEquals(IgdbLookup.Failed, repository(failing).findEstimatedPlaytime("Hades", 2020, null))
    }

    @Test
    fun `un jeu sans duree ni jaquette est quand meme renvoye`() = runTest {
        val bare = igdbGame(5, "Sans rien", type = null, platforms = null)
        val api = FakeIgdbApi(listOf(bare))

        val match = repository(api).findEstimatedPlaytime("Sans rien", null, 5L).found()

        assertNull(match.platform)
        assertNull(match.coverUrl)
        assertNull(match.releaseYear)
        assertNull(match.playtimeEstimate)
    }
}
