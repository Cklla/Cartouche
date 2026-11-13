package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.domain.model.GameSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilteringTest {

    @Test
    fun `normalizeTitle ignore la casse et les espaces superflus`() {
        assertTrue("Hades" == "Hades")
        assertTrue(normalizeTitle(" Hades ") == normalizeTitle("hades"))
    }

    @Test
    fun `isAlreadyAdded detecte un titre deja dans le backlog quelle que soit la casse`() {
        val backlog = mapOf(normalizeTitle("The Legend of Zelda") to "id-1")
        assertTrue(isAlreadyAdded("the legend of zelda", backlog))
    }

    @Test
    fun `isAlreadyAdded renvoie faux pour un titre absent du backlog`() {
        val backlog = mapOf(normalizeTitle("Hades") to "id-1")
        assertFalse(isAlreadyAdded("Elden Ring", backlog))
    }

    @Test
    fun `backlogGameId renvoie l'id du jeu deja ajoute quelle que soit la casse`() {
        val backlog = mapOf(normalizeTitle("Hades") to "id-1")
        assertEquals("id-1", backlogGameId("HADES", backlog))
    }

    @Test
    fun `backlogGameId renvoie null pour un titre absent du backlog`() {
        val backlog = mapOf(normalizeTitle("Hades") to "id-1")
        assertNull(backlogGameId("Elden Ring", backlog))
    }

    private fun result(igdbId: Long, title: String) =
        GameSearchResult(igdbId = igdbId, title = title, platform = "", genre = "", year = "", coverUrl = null)

    @Test
    fun `un resultat est reconnu par son id IGDB meme si le titre differe`() {
        val byIgdb = mapOf(2364L to "id-1")

        assertEquals("id-1", backlogGameId(result(2364, "Xenoblade Chronicles"), byIgdb, emptyMap()))
        assertEquals("id-1", backlogGameId(result(2364, "Autre titre"), byIgdb, mapOf(normalizeTitle("Autre titre") to "id-2")))
    }

    @Test
    fun `un resultat est reconnu par son titre pour un jeu ajoute sans id IGDB`() {
        val byTitle = mapOf(normalizeTitle("Hades") to "id-3")

        assertEquals("id-3", backlogGameId(result(113112, "hades"), emptyMap(), byTitle))
    }

    @Test
    fun `l'id IGDB est prioritaire sur le titre et le titre reste le filet des jeux sans id`() {
        val byIgdb = mapOf(1L to "id-1")
        val byTitle = mapOf(normalizeTitle("Prey") to "id-2")

        assertEquals("id-1", backlogGameId(result(1, "Prey"), byIgdb, byTitle))
        assertEquals("id-2", backlogGameId(result(2, "Prey"), byIgdb, byTitle))
        assertNull(backlogGameId(result(3, "Autre"), byIgdb, byTitle))
    }

    @Test
    fun `un resultat absent du backlog n'a pas d'id`() {
        assertNull(backlogGameId(result(9, "Inconnu"), emptyMap(), emptyMap()))
    }
}
