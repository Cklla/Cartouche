package fr.cklla.cartouche.domain.description

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDescriptionTest {

    private fun content(
        igdbId: Long = 113112,
        summary: String? = "Original",
        summaryFr: String? = "Traduction",
        model: String? = "un-modele",
    ) = DescriptionContent(igdbId, summary, summaryFr, model)

    @Test
    fun `un contenu complet, ou pas de description entierement nul, respecte les bornes`() {
        assertTrue(content().isWithinLimits())
        assertTrue(emptyContent(113112).isWithinLimits())
        // Un original sans traduction (ligne écrite par un autre client) reste admis.
        assertTrue(content(summaryFr = null, model = null).isWithinLimits())
    }

    @Test
    fun `un id IGDB hors borne est refuse`() {
        assertFalse(content(igdbId = 0).isWithinLimits())
        assertFalse(content(igdbId = -4).isWithinLimits())
        assertFalse(content(igdbId = 10_000_000_000).isWithinLimits())
        assertTrue(content(igdbId = 9_999_999_999).isWithinLimits())
    }

    @Test
    fun `un texte vide ou blanc est refuse, pas de description s ecrit null jamais vide`() {
        assertFalse(content(summary = "").isWithinLimits())
        assertFalse(content(summary = "  \n ").isWithinLimits())
        assertFalse(content(summaryFr = "").isWithinLimits())
        assertFalse(content(summaryFr = " \t").isWithinLimits())
    }

    @Test
    fun `les bornes de la table sont respectees, comptees en points de code`() {
        assertTrue(content(summary = "a".repeat(5000), summaryFr = "b".repeat(10_000)).isWithinLimits())
        assertFalse(content(summary = "a".repeat(5001)).isWithinLimits())
        assertFalse(content(summaryFr = "b".repeat(10_001)).isWithinLimits())
        // 5000 émojis = 10 000 unités UTF-16 mais 5000 points de code : accepté par Postgres, donc ici.
        assertTrue(content(summary = "😀".repeat(5000)).isWithinLimits())
        assertFalse(content(summary = "😀".repeat(5001)).isWithinLimits())
        assertTrue(content(model = "m".repeat(100)).isWithinLimits())
        assertFalse(content(model = "m".repeat(101)).isWithinLimits())
    }

    @Test
    fun `une traduction ne va pas sans original ni sans le modele qui l a produite`() {
        assertFalse(content(summary = null).isWithinLimits())
        assertFalse(content(summary = null, summaryFr = null).copy(translationModel = "m").isWithinLimits())
        assertFalse(content(model = null).isWithinLimits())
        assertFalse(content(model = "").isWithinLimits())
        assertFalse(content(summaryFr = null, model = "m").isWithinLimits())
    }

    @Test
    fun `un texte long ou a nombreuses lignes est replie, pas un texte court`() {
        assertFalse(needsFold("a".repeat(FOLD_LENGTH)))
        assertTrue(needsFold("a".repeat(FOLD_LENGTH + 1)))
        assertFalse(needsFold("a\nb\nc\nd"))
        assertTrue(needsFold("a\nb\nc\nd\ne"))
        assertFalse(needsFold(""))
    }

    @Test
    fun `une description n est valable que pour l id IGDB dont elle est tiree`() {
        val description = GameDescription("g1", content(igdbId = 7))
        assertTrue(description.isCurrent(7))
        assertFalse(description.isCurrent(8))
    }

    @Test
    fun `la vue sans original est vide, avec original elle porte les deux textes`() {
        assertEquals(DescriptionView.None, emptyContent(1).toView(saved = true))
        assertEquals(
            DescriptionView.Ready("Original", "Traduction", saved = false),
            content().toView(saved = false),
        )
    }
}
