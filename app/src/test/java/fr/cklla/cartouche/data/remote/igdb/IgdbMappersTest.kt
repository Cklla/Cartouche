package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgdbMappersTest {

    @Test
    fun `buildSearchQuery construit une requete Apicalypse de recherche par titre`() {
        assertEquals(
            "search \"Hades\"; fields id,name; limit 10;",
            buildSearchQuery("Hades"),
        )
    }

    @Test
    fun `buildSearchQuery echappe les guillemets du titre pour ne pas casser la requete`() {
        val titleWithQuotes = "Baldur's Gate 3: " + "\"Honour Mode\""
        val expected = "search \"Baldur's Gate 3: " + "\\\"Honour Mode\\\"" + "\"; fields id,name; limit 10;"

        assertEquals(expected, buildSearchQuery(titleWithQuotes))
    }

    @Test
    fun `buildTimeToBeatQuery filtre sur l'id du jeu IGDB trouve`() {
        assertEquals(
            "fields hastily,normally,completely; where game_id = 42; limit 1;",
            buildTimeToBeatQuery(42L),
        )
    }

    @Test
    fun `findBestMatch privilegie une correspondance exacte de titre`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Hades II"),
            IgdbGameDto(id = 2, name = "Hades"),
        )
        assertEquals(2L, findBestMatch("Hades", candidates)?.id)
    }

    @Test
    fun `findBestMatch tolere la casse et la ponctuation`() {
        val candidates = listOf(IgdbGameDto(id = 1, name = "Hades"))
        assertEquals(1L, findBestMatch("  hades!!  ", candidates)?.id)
    }

    @Test
    fun `findBestMatch renvoie le candidat le plus proche au-dessus du seuil de similarite`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Metal Gear Solid"),
            IgdbGameDto(id = 2, name = "Persona 3 Reloaded"),
        )
        assertEquals(2L, findBestMatch("Persona 3 Reload", candidates)?.id)
    }

    @Test
    fun `findBestMatch renvoie null sans candidat`() {
        assertNull(findBestMatch("Hades", emptyList()))
    }

    @Test
    fun `findBestMatch renvoie null si aucun candidat n'est assez proche`() {
        val candidates = listOf(IgdbGameDto(id = 1, name = "Complètement Autre Chose"))
        assertNull(findBestMatch("Hades", candidates))
    }

    @Test
    fun `secondsToHours convertit des secondes en heures entieres`() {
        assertEquals(10, secondsToHours(36_000))
        assertEquals(0, secondsToHours(1_800))
    }
}
