package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgdbMappersTest {

    @Test
    fun `buildSearchQuery construit une requete Apicalypse de recherche par titre`() {
        assertEquals(
            "search \"Hades\"; fields id,name,first_release_date; limit 30;",
            buildSearchQuery("Hades"),
        )
    }

    @Test
    fun `buildSearchQuery echappe les guillemets du titre pour ne pas casser la requete`() {
        val titleWithQuotes = "Baldur's Gate 3: " + "\"Honour Mode\""
        val expected = "search \"Baldur's Gate 3: " + "\\\"Honour Mode\\\"" +
            "\"; fields id,name,first_release_date; limit 30;"

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
    fun `findBestMatch retrouve le jeu de base parmi une recherche noyee par ses DLC (cas reel Persona 3 Reload)`() {
        // Données réelles observées sur IGDB (search "Persona 3 Reload") : le jeu de base
        // (id 252647) est noyé en position 11/20 par du contenu additionnel au nom proche — c'est
        // ce qui motive la limite de 30 candidats dans `buildSearchQuery` plutôt qu'un filtre par
        // catégorie IGDB (le jeu de base n'a lui-même pas de catégorie renseignée, voir ce champ).
        val candidates = listOf(
            IgdbGameDto(id = 266009, name = "Persona 3 Reload: Persona 5 Royal Persona Set 1", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 301578, name = "Persona 3 Reload: Persona 4 Golden Persona Set", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 266008, name = "Persona 3 Reload: Persona 5 Royal Persona Set 2", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 301573, name = "Persona 3 Reload: Persona 5 Royal BGM Set", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 289702, name = "Persona 3 Reload: Persona 5 Royal EX BGM Set", firstReleaseDate = 1_710_201_600L),
            IgdbGameDto(id = 289701, name = "Persona 3 Reload: Persona 4 Golden EX BGM Set", firstReleaseDate = 1_710_201_600L),
            IgdbGameDto(id = 327019, name = "Persona 3 Reload: FeMC Mod"),
            IgdbGameDto(id = 301572, name = "Persona 3 Reload: Persona 5 Royal Shujin Academy Costume Set", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 301577, name = "Persona 3 Reload: Persona 5 Royal Phantom Thieves Costume Set", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 301567, name = "Persona 3 Reload: Persona 4 Golden Yasogami High Costume Set", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 252647, name = "Persona 3 Reload", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 266007, name = "Persona 3 Reload: DLC Pack", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 262641, name = "Persona 3 Reload: Limited Box", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 289704, name = "Persona 3 Reload: Expansion Pass", firstReleaseDate = 1_710_201_600L),
            IgdbGameDto(id = 289703, name = "Persona 3 Reload: Episode Aigis", firstReleaseDate = 1_725_926_400L),
            IgdbGameDto(id = 262640, name = "Persona 3 Reload: Aigis Edition", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 328622, name = "Persona 3 Reload: FemC Reloaded Project", firstReleaseDate = 1_707_523_200L),
            IgdbGameDto(id = 262642, name = "Persona 3 Reload: Digital Deluxe Edition", firstReleaseDate = 1_706_832_000L),
            IgdbGameDto(id = 289700, name = "Persona 3 Reload: Velvet Costume & BGM Set", firstReleaseDate = 1_714_521_600L),
            IgdbGameDto(id = 262643, name = "Persona 3 Reload: Digital Premium Edition", firstReleaseDate = 1_706_832_000L),
        )
        assertEquals(252647L, findBestMatch("Persona 3 Reload", 2024, candidates)?.id)
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
