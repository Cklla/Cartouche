package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenoblade
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeDefinitive
import fr.cklla.cartouche.data.remote.igdb.IgdbSamples.xenobladeSwitch2
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbPlatformDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbGameMergeTest {

    // --- Filtre de type : un champ absent n'écarte jamais un jeu ---

    @Test
    fun `les types exclus sont ecartes et les types gardes conserves`() {
        listOf(1L, 2L, 3L, 5L, 6L, 7L, 12L, 13L, 14L).forEach { excluded ->
            assertFalse("type $excluded", igdbGame(1, "x", type = excluded).isKeptGameType())
        }
        listOf(0L, 4L, 8L, 9L, 10L, 11L).forEach { kept ->
            assertTrue("type $kept", igdbGame(1, "x", type = kept).isKeptGameType())
        }
    }

    @Test
    fun `un jeu sans game_type est garde`() {
        assertTrue(igdbGame(1, "Jeu sans type", type = null).isKeptGameType())
    }

    // --- Règle de rattachement ---

    @Test
    fun `une edition se rattache a son version_parent`() {
        assertEquals(2364L, igdbGame(1, "Collector", type = 0, versionParent = 2364).mergeParentId())
        assertEquals(2364L, igdbGame(1, "Edition sans type", type = null, versionParent = 2364).mergeParentId())
    }

    @Test
    fun `un portage se rattache a son parent_game`() {
        assertEquals(2364L, igdbGame(1, "3D", type = 11, parentGame = 2364).mergeParentId())
    }

    @Test
    fun `un jeu etendu, un remake, un remaster et une extension autonome restent des fiches a part`() {
        // Ex. *Divinity: Original Sin - Enhanced Edition* (type 10, parent_game = jeu de base).
        listOf(10L, 8L, 9L, 4L).forEach { type ->
            assertNull("type $type", igdbGame(1, "x", type = type, parentGame = 1029, versionParent = 1029).mergeParentId())
        }
    }

    @Test
    fun `un jeu de base sans parent n'a pas de parent`() {
        assertNull(igdbGame(1, "Hades").mergeParentId())
        assertNull(igdbGame(1, "Hades", type = null).mergeParentId())
    }

    // --- Fusion sur deux niveaux ---

    @Test
    fun `une edition d'un portage est fusionnee dans le jeu de base sur deux niveaux`() {
        val base = igdbGame(1, "Jeu", platforms = listOf("Wii"))
        val port = igdbGame(2, "Jeu 3D", type = 11, parentGame = 1, platforms = listOf("Nintendo 3DS"))
        val portEdition = igdbGame(3, "Jeu 3D Collector", type = 0, versionParent = 2, platforms = listOf("New Nintendo 3DS"))

        val merged = buildMergedGames(listOf(base, port, portEdition))

        assertEquals(listOf(1L), merged.map { it.game.id })
        assertEquals("New Nintendo 3DS/Nintendo 3DS/Wii", formatIgdbPlatforms(merged.single().platforms))
    }

    @Test
    fun `un jeu etendu a sa propre fiche, avec ses propres editions`() {
        val merged = buildMergedGames(listOf(xenoblade, xenobladeDefinitive, xenobladeSwitch2)).associateBy { it.game.id }

        assertEquals(setOf(xenoblade.id, xenobladeDefinitive.id), merged.keys)
        assertEquals("Wii/Wii U", formatIgdbPlatforms(merged.getValue(xenoblade.id).platforms))
        assertEquals(
            "Nintendo Switch/Nintendo Switch 2",
            formatIgdbPlatforms(merged.getValue(xenobladeDefinitive.id).platforms),
        )
    }

    @Test
    fun `un jeu dont le parent est hors du lot garde sa propre fiche`() {
        val merged = buildMergedGames(listOf(xenobladeDefinitive))

        assertEquals(listOf(xenobladeDefinitive.id), merged.map { it.game.id })
    }

    @Test
    fun `un cycle de parentes ne fait ni boucle ni disparaitre les jeux`() {
        val a = igdbGame(1, "A", type = 0, versionParent = 2)
        val b = igdbGame(2, "B", type = 0, versionParent = 1)

        val merged = buildMergedGames(listOf(a, b))

        assertTrue(merged.isNotEmpty())
    }

    // --- Ordre ---

    @Test
    fun `l'ordre place le titre exact, puis le titre qui commence par la recherche, puis le nombre de notes`() {
        val exact = MergedGame(igdbGame(1, "Hades", ratings = 1), emptyList())
        val startsWith = MergedGame(igdbGame(2, "Hades II", ratings = 500), emptyList())
        val popular = MergedGame(igdbGame(3, "Super Hades Clone", ratings = 900), emptyList())
        val unknown = MergedGame(igdbGame(4, "Autre Hades Clone", ratings = null), emptyList())

        val ranked = rankByRelevance("hades", listOf(unknown, popular, startsWith, exact))

        assertEquals(listOf(1L, 2L, 3L, 4L), ranked.map { it.game.id })
    }

    @Test
    fun `l'ordre conserve celui d'IGDB a criteres egaux`() {
        val first = MergedGame(igdbGame(1, "Zelda A", ratings = 10), emptyList())
        val second = MergedGame(igdbGame(2, "Zelda B", ratings = 10), emptyList())

        assertEquals(listOf(1L, 2L), rankByRelevance("zelda", listOf(first, second)).map { it.game.id })
        assertEquals(listOf(2L, 1L), rankByRelevance("zelda", listOf(second, first)).map { it.game.id })
    }

    // --- Jaquette ---

    @Test
    fun `la jaquette est construite en HTTPS sur images igdb com depuis un image_id valide`() {
        assertEquals(
            "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/coc8ap.jpg",
            buildCoverUrl("coc8ap"),
        )
    }

    @Test
    fun `un image_id invalide ou absent ne donne aucune jaquette`() {
        listOf(
            null,
            "",
            "CO8AP", // majuscules
            "co 8ap",
            "../../evil",
            "co8ap.jpg",
            "co8ap/../x",
            "co8ap?x=1",
            "http://evil.example/x",
            "a".repeat(41), // trop long
        ).forEach { assertNull("image_id <$it>", buildCoverUrl(it)) }
    }

    // --- Résultat de recherche ---

    @Test
    fun `un resultat reprend titre, annee, genre, jaquette et les plateformes fusionnees`() {
        val merged = MergedGame(
            game = xenoblade,
            platforms = listOf("Wii", "Nintendo Switch", "PC (Microsoft Windows)", "Linux").map(::IgdbPlatformDto),
        )

        val result = merged.toSearchResult()!!

        assertEquals(xenoblade.id, result.igdbId)
        assertEquals("Xenoblade Chronicles", result.title)
        assertEquals("2010", result.year)
        assertEquals("Role-playing (RPG)", result.genre)
        assertEquals("Nintendo Switch/PC/Wii", result.platform)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/coc8ap.jpg", result.coverUrl)
    }

    @Test
    fun `un jeu sans jaquette, sans date, sans genre ni plateforme reste un resultat valide`() {
        val result = MergedGame(igdbGame(5, "Mystère", type = null, platforms = null), emptyList()).toSearchResult()!!

        assertEquals("", result.year)
        assertEquals("", result.genre)
        assertEquals("", result.platform)
        assertNull(result.coverUrl)
    }

    @Test
    fun `un jeu dont l'id sort de la borne du serveur n'est pas propose`() {
        assertNull(MergedGame(igdbGame(0, "x"), emptyList()).toSearchResult())
        assertNull(MergedGame(igdbGame(10_000_000_000L, "x"), emptyList()).toSearchResult())
    }
}
