package fr.cklla.cartouche.ui.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnualRecapTest {

    @Test
    fun `24 decembre ne declenche pas de recap`() {
        assertNull(recapTargetYear(LocalDate.of(2026, 12, 24)))
    }

    @Test
    fun `25 decembre declenche le recap de l'annee en cours`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun `31 decembre cible toujours l'annee en cours`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `1er janvier bascule vers l'annee precedente comme cible`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `31 janvier est le dernier jour ou le recap s'affiche encore`() {
        assertEquals(2026, recapTargetYear(LocalDate.of(2027, 1, 31)))
    }

    @Test
    fun `1er fevrier ne declenche plus de recap`() {
        assertNull(recapTargetYear(LocalDate.of(2027, 2, 1)))
    }
}
