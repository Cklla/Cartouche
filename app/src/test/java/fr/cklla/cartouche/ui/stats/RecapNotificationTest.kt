package fr.cklla.cartouche.ui.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecapNotificationTest {

    @Test
    fun `hors fenetre de recap, pas de notification meme si jamais notifie`() {
        assertNull(recapYearToNotify(LocalDate.of(2026, 6, 15), alreadyNotified = { false }))
    }

    @Test
    fun `dans la fenetre et jamais notifie pour cette annee, notifie`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2026, 12, 25), alreadyNotified = { false }))
    }

    @Test
    fun `dans la fenetre mais deja notifie pour cette annee precise, ne renotifie pas`() {
        assertNull(recapYearToNotify(LocalDate.of(2026, 12, 28), alreadyNotified = { year -> year == 2026 }))
    }

    @Test
    fun `deja notifie pour une autre annee ne bloque pas la notification de l'annee ciblee`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2026, 12, 25), alreadyNotified = { year -> year == 2025 }))
    }

    @Test
    fun `bascule sur l'annee precedente en janvier, meme logique de flag deja-notifie`() {
        assertEquals(2026, recapYearToNotify(LocalDate.of(2027, 1, 15), alreadyNotified = { false }))
        assertNull(recapYearToNotify(LocalDate.of(2027, 1, 15), alreadyNotified = { year -> year == 2026 }))
    }
}
