package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformsTest {

    @Test
    fun `parsePlatforms decoupe une chaine multi-plateformes jointe par des slash`() {
        assertEquals(listOf("PC", "PS5", "Switch"), parsePlatforms("PC/PS5/Switch"))
    }

    @Test
    fun `parsePlatforms renvoie une liste d'un seul element sans slash`() {
        assertEquals(listOf("PC"), parsePlatforms("PC"))
    }

    @Test
    fun `parsePlatforms ignore les espaces autour de chaque plateforme`() {
        assertEquals(listOf("PC", "PS5"), parsePlatforms("PC / PS5"))
    }

    @Test
    fun `parsePlatforms renvoie une liste vide pour une chaine vide`() {
        assertEquals(emptyList<String>(), parsePlatforms(""))
    }

    @Test
    fun `parsePlatforms deduplique les plateformes identiques`() {
        assertEquals(listOf("PC"), parsePlatforms("PC/PC"))
    }
}
