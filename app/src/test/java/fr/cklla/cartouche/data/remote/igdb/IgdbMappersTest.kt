package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgdbMappersTest {

    @Test
    fun `buildSearchQuery construit une requete Apicalypse de recherche par titre`() {
        assertEquals(
            "search \"Hades\"; fields id,name,first_release_date; limit 10;",
            buildSearchQuery("Hades"),
        )
    }

    @Test
    fun `buildSearchQuery echappe les guillemets du titre pour ne pas casser la requete`() {
        val titleWithQuotes = "Baldur's Gate 3: " + "\"Honour Mode\""
        val expected = "search \"Baldur's Gate 3: " + "\\\"Honour Mode\\\"" +
            "\"; fields id,name,first_release_date; limit 10;"

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
    fun `buildSteamExternalGameQuery filtre sur l'App ID Steam et la categorie Steam`() {
        assertEquals(
            "fields game; where uid = \"1145360\" & category = 1; limit 1;",
            buildSteamExternalGameQuery(1145360L),
        )
    }

    @Test
    fun `findBestMatch privilegie une correspondance exacte de titre`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Hades II"),
            IgdbGameDto(id = 2, name = "Hades"),
        )
        assertEquals(2L, findBestMatch("Hades", null, candidates)?.id)
    }

    @Test
    fun `findBestMatch tolere la casse et la ponctuation`() {
        val candidates = listOf(IgdbGameDto(id = 1, name = "Hades"))
        assertEquals(1L, findBestMatch("  hades!!  ", null, candidates)?.id)
    }

    @Test
    fun `findBestMatch ignore un suffixe entre parentheses cote IGDB (annee, mention diverse)`() {
        // Cas d'usage réel ayant motivé cette évolution : RAWG "Persona 3 Reload" ne matchait pas
        // IGDB "Persona 3 Reload (2024)" à cause du suffixe d'année entre parenthèses.
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Persona 3 Reload (2024)"),
            IgdbGameDto(id = 2, name = "Metal Gear Solid"),
        )
        assertEquals(1L, findBestMatch("Persona 3 Reload", null, candidates)?.id)
    }

    @Test
    fun `findBestMatch ignore une mention d'edition cote IGDB`() {
        val candidates = listOf(IgdbGameDto(id = 1, name = "Fallout 4: Game of the Year Edition"))
        assertEquals(1L, findBestMatch("Fallout 4", null, candidates)?.id)
    }

    @Test
    fun `findBestMatch privilegie le candidat dont l'annee de sortie correspond parmi plusieurs noms proches`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Persona 3 Reloaded", firstReleaseDate = 1_009_843_200L), // 2002
            IgdbGameDto(id = 2, name = "Persona 3 Reloaded", firstReleaseDate = 1_704_067_200L), // 2024
        )
        assertEquals(2L, findBestMatch("Persona 3 Reload", 2024, candidates)?.id)
    }

    @Test
    fun `findBestMatch retombe sur la meilleure similarite si aucune annee ne correspond`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Persona 3 Reloaded", firstReleaseDate = 1_009_843_200L), // 2002
        )
        assertEquals(1L, findBestMatch("Persona 3 Reload", 2024, candidates)?.id)
    }

    @Test
    fun `findBestMatch renvoie le candidat le plus proche au-dessus du seuil de similarite`() {
        val candidates = listOf(
            IgdbGameDto(id = 1, name = "Metal Gear Solid"),
            IgdbGameDto(id = 2, name = "Persona 3 Reloaded"),
        )
        assertEquals(2L, findBestMatch("Persona 3 Reload", null, candidates)?.id)
    }

    @Test
    fun `findBestMatch renvoie null sans candidat`() {
        assertNull(findBestMatch("Hades", null, emptyList()))
    }

    @Test
    fun `findBestMatch renvoie null si aucun candidat n'est assez proche`() {
        val candidates = listOf(IgdbGameDto(id = 1, name = "Complètement Autre Chose"))
        assertNull(findBestMatch("Hades", null, candidates))
    }

    @Test
    fun `igdbReleaseYear convertit le timestamp Unix en annee`() {
        assertEquals(2024, igdbReleaseYear(IgdbGameDto(id = 1, name = "x", firstReleaseDate = 1_704_067_200L)))
    }

    @Test
    fun `igdbReleaseYear renvoie null sans date de sortie connue`() {
        assertNull(igdbReleaseYear(IgdbGameDto(id = 1, name = "x", firstReleaseDate = null)))
    }

    @Test
    fun `toEstimatedHours convertit des secondes connues en heures entieres`() {
        assertEquals(10, toEstimatedHours(36_000))
    }

    @Test
    fun `toEstimatedHours traite l'absence de donnee comme non disponible`() {
        assertNull(toEstimatedHours(null))
    }

    @Test
    fun `toEstimatedHours traite une duree a zero comme non disponible`() {
        assertNull(toEstimatedHours(0))
    }
}
