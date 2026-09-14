package fr.cklla.cartouche.ui.recherche

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
}
