package fr.cklla.cartouche.ui.recherche

import org.junit.Assert.assertFalse
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
        val backlog = setOf(normalizeTitle("The Legend of Zelda"))
        assertTrue(isAlreadyAdded("the legend of zelda", backlog))
    }

    @Test
    fun `isAlreadyAdded renvoie faux pour un titre absent du backlog`() {
        val backlog = setOf(normalizeTitle("Hades"))
        assertFalse(isAlreadyAdded("Elden Ring", backlog))
    }
}
