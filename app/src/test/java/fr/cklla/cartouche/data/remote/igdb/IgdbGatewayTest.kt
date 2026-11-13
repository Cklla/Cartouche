package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.masterQuest
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.ocarina
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.ocarina3d
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.ocarina3dFirstEdition
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.ocarinaSwitch2
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenoblade
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenoblade3
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenoblade3d
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeCollector
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeDefinitive
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeDlc
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeSwitch2
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbGatewayTest {

    private val xenobladeCatalog = listOf(
        xenoblade, xenobladeDefinitive, xenobladeSwitch2, xenoblade3d, xenobladeCollector, xenobladeDlc, xenoblade3,
    )

    private fun gateway(api: FakeIgdbApi, token: String? = "jeton") = IgdbGateway(api, FakeIgdbTokenSource(token))

    // --- Xenoblade : les plateformes de l'édition Switch 2 remontent sur le jeu de base ---

    @Test
    fun `Xenoblade reunit Wii, Wii U, Switch, Switch 2 et 3DS sur une seule fiche`() = runTest {
        val api = FakeIgdbApi(
            catalog = xenobladeCatalog,
            // IGDB renvoie le jeu de base, l'édition définitive et l'autre épisode, pas l'édition Switch 2.
            searchResults = listOf(xenoblade.id, xenoblade3.id, xenobladeDefinitive.id),
        )

        val results = gateway(api).search("xenoblade chronicles")

        val base = results.single { it.game.id == xenoblade.id }
        assertEquals(
            "New Nintendo 3DS/Nintendo 3DS/Nintendo Switch/Nintendo Switch 2/Wii/Wii U",
            formatIgdbPlatforms(base.platforms),
        )
        // L'édition définitive n'apparaît plus seule ; le DLC n'est jamais fusionné.
        assertFalse(results.any { it.game.id == xenobladeDefinitive.id })
        assertFalse(formatIgdbPlatforms(base.platforms)!!.contains("PlayStation 5"))
        assertTrue(results.any { it.game.id == xenoblade3.id })
    }

    @Test
    fun `une edition cherchee seule est ramenee a son jeu de base`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog, searchResults = listOf(xenobladeDefinitive.id))

        val results = gateway(api).search("xenoblade chronicles definitive edition")

        assertEquals(listOf(xenoblade.id), results.map { it.game.id })
        assertTrue(formatIgdbPlatforms(results.single().platforms)!!.contains("Nintendo Switch 2"))
    }

    @Test
    fun `une edition sans type de jeu est rattachee et un jeu sans type reste un resultat`() = runTest {
        val untypedEdition = igdbGame(61, "Hades: Édition sans type", type = null, versionParent = 60, platforms = listOf("PlayStation 5"))
        val base = igdbGame(60, "Hades", type = null, platforms = listOf("PC (Microsoft Windows)"))
        val api = FakeIgdbApi(catalog = listOf(base, untypedEdition), searchResults = listOf(60))

        val results = gateway(api).search("hades")

        assertEquals(listOf(60L), results.map { it.game.id })
        assertEquals("PC/PlayStation 5", formatIgdbPlatforms(results.single().platforms))
    }

    // --- Ocarina : remakes et extension autonome restent des fiches à part ---

    @Test
    fun `Ocarina of Time, ses remakes et Master Quest sont des fiches separees`() = runTest {
        val api = FakeIgdbApi(
            catalog = listOf(ocarina, ocarina3d, ocarina3dFirstEdition, ocarinaSwitch2, masterQuest),
            searchResults = listOf(ocarina.id, ocarina3d.id, masterQuest.id, ocarinaSwitch2.id, ocarina3dFirstEdition.id),
        )

        val results = gateway(api).search("the legend of zelda: ocarina of time")

        assertEquals(
            setOf(ocarina.id, ocarina3d.id, ocarinaSwitch2.id, masterQuest.id),
            results.map { it.game.id }.toSet(),
        )
        // Le jeu d'origine n'hérite ni du remake 3DS ni du remake Switch 2.
        assertEquals("64DD/Nintendo 64/Wii/Wii U", formatIgdbPlatforms(results.single { it.game.id == ocarina.id }.platforms))
        assertEquals("Nintendo Switch 2", formatIgdbPlatforms(results.single { it.game.id == ocarinaSwitch2.id }.platforms))
        // L'édition « First Edition » du remake 3DS est fusionnée dans le remake.
        assertEquals("Nintendo 3DS", formatIgdbPlatforms(results.single { it.game.id == ocarina3d.id }.platforms))
    }

    @Test
    fun `au plus vingt resultats sont renvoyes`() = runTest {
        val many = (1L..40L).map { igdbGame(it, "Jeu $it", platforms = listOf("PC (Microsoft Windows)")) }
        val api = FakeIgdbApi(catalog = many, searchResults = many.map { it.id })

        assertEquals(MAX_SEARCH_RESULTS, gateway(api).search("jeu").size)
    }

    // --- Lecture par id ---

    @Test
    fun `lire un jeu par id fusionne les plateformes de ses editions`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog)

        val merged = gateway(api).loadById(xenoblade.id)!!

        assertEquals(xenoblade.id, merged.game.id)
        assertTrue(formatIgdbPlatforms(merged.platforms)!!.contains("Nintendo Switch 2"))
    }

    @Test
    fun `un id hors borne n'envoie aucune requete`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog)

        assertNull(gateway(api).loadById(0))
        assertNull(gateway(api).loadById(-1))
        assertNull(gateway(api).loadById(10_000_000_000L))
        assertNull(gateway(api).timeToBeat(0))
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun `un id inconnu d'IGDB renvoie null`() = runTest {
        assertNull(gateway(FakeIgdbApi(catalog = emptyList())).loadById(123))
    }

    @Test
    fun `les durees de jeu sont lues par game_id`() = runTest {
        val api = FakeIgdbApi(
            catalog = xenobladeCatalog,
            timeToBeat = IgdbTimeToBeatDto(hastilySeconds = 0, normallySeconds = 252_000, completelySeconds = null),
        )

        val ttb = gateway(api).timeToBeat(xenoblade.id)

        assertEquals(252_000, ttb?.normallySeconds)
        assertEquals("fields hastily,normally,completely; where game_id = ${xenoblade.id}; limit 1;", api.queries.last())
    }

    // --- Erreurs et débit ---

    @Test
    fun `un 429 est retente une fois`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog, searchResults = listOf(xenoblade3.id)).apply { rateLimitFailures = 1 }

        val results = gateway(api).search("xenoblade chronicles 3")

        assertEquals(listOf(xenoblade3.id), results.map { it.game.id })
    }

    @Test(expected = retrofit2.HttpException::class)
    fun `un 429 persistant remonte`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog, searchResults = listOf(xenoblade3.id)).apply { rateLimitFailures = 2 }

        gateway(api).search("xenoblade chronicles 3")
    }

    @Test(expected = IOException::class)
    fun `une panne reseau remonte`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog).apply { failure = IOException("hors ligne") }

        gateway(api).search("xenoblade")
    }

    @Test(expected = IgdbUnavailableException::class)
    fun `sans jeton IGDB, aucune requete n'est envoyee`() = runTest {
        val api = FakeIgdbApi(catalog = xenobladeCatalog)

        try {
            gateway(api, token = null).search("xenoblade")
        } finally {
            assertTrue(api.queries.isEmpty())
        }
    }
}
