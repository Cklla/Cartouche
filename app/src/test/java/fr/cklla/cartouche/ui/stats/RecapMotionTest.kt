package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapMotionTest {

    @Test
    fun `une animation n'a pas commence avant son delai`() {
        assertEquals(0f, entryProgress(timeMillis = 100f, delayMillis = 120, durationMillis = 400), 0f)
    }

    @Test
    fun `une animation est terminee apres delai et duree`() {
        assertEquals(1f, entryProgress(timeMillis = 520f, delayMillis = 120, durationMillis = 400), 0f)
        assertEquals(1f, entryProgress(timeMillis = 5_000f, delayMillis = 120, durationMillis = 400), 0f)
    }

    @Test
    fun `l'avancement est croissant et ralentit en fin de course`() {
        val samples = (0..10).map { entryProgress(it * 40f, delayMillis = 0, durationMillis = 400) }

        assertEquals(samples.sorted(), samples)
        val firstStep = samples[1] - samples[0]
        val lastStep = samples[10] - samples[9]
        assertTrue("démarre vite : $firstStep", firstStep > lastStep)
    }

    @Test
    fun `la ligne de temps couvre chaque animation d'entree`() {
        val ends = buildList {
            add(RecapMotion.TITLE_DELAY + RecapMotion.TITLE_DURATION)
            add(RecapMotion.KEY_FIGURE_DELAY + RecapMotion.KEY_FIGURE_DURATION)
            add(RecapMotion.COUNT_DELAY + RecapMotion.COUNT_DURATION)
            add(RecapMotion.RING_DELAY + RecapMotion.RING_DURATION)
            // Une liste très longue plafonne son décalage : la dernière ligne entre avec la quatrième.
            add(RecapMotion.detailDelay(50) + RecapMotion.DETAIL_DURATION)
            repeat(RecapMotion.COVER_DELAYS.size) { add(RecapMotion.coverDelay(it) + RecapMotion.COVER_DURATION) }
        }

        assertTrue(ends.max() <= RecapMotion.ENTRY_TIMELINE)
    }

    @Test
    fun `les jaquettes au-dela de la troisieme reprennent le dernier delai`() {
        assertEquals(240, RecapMotion.coverDelay(0))
        assertEquals(320, RecapMotion.coverDelay(1))
        assertEquals(400, RecapMotion.coverDelay(2))
        assertEquals(400, RecapMotion.coverDelay(9))
    }

    @Test
    fun `les cartes d'une liste se decalent puis le decalage est plafonne`() {
        assertEquals(240, RecapMotion.detailDelay(0))
        assertEquals(320, RecapMotion.detailDelay(1))
        assertEquals(480, RecapMotion.detailDelay(3))
        assertEquals(480, RecapMotion.detailDelay(12))
    }

    @Test
    fun `le comptage va de 0 au total par entiers`() {
        assertEquals(0, countUpValue(19, 0f))
        assertEquals(10, countUpValue(19, 0.5f))
        assertEquals(19, countUpValue(19, 1f))
    }

    @Test
    fun `seule une echelle d'animation a zero reduit les animations`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
    }

    private val terminé = StatusRingSegment(GameStatus.TERMINE, startAngle = -90f, sweepAngle = 270f)
    private val abandonné = StatusRingSegment(GameStatus.ABANDONNE, startAngle = 180f, sweepAngle = 90f)

    @Test
    fun `les tranches de l'anneau sont proportionnelles aux effectifs et partent du haut`() {
        val segments = statusRingSegments(completedCount = 3, abandonedCount = 1)

        assertEquals(listOf(GameStatus.TERMINE, GameStatus.ABANDONNE), segments.map { it.status })
        assertEquals(-90f, segments[0].startAngle, 0.001f)
        assertEquals(270f, segments[0].sweepAngle, 0.001f)
        assertEquals(180f, segments[1].startAngle, 0.001f)
        assertEquals(90f, segments[1].sweepAngle, 0.001f)
    }

    @Test
    fun `un effectif nul n'a pas de tranche`() {
        assertEquals(listOf(GameStatus.TERMINE), statusRingSegments(4, 0).map { it.status })
        assertEquals(listOf(GameStatus.ABANDONNE), statusRingSegments(0, 4).map { it.status })
        assertTrue(statusRingSegments(0, 0).isEmpty())
    }

    @Test
    fun `l'anneau est vide au depart et complet a la fin`() {
        assertEquals(0f, revealedSweep(terminé, 0f), 0f)
        assertEquals(270f, revealedSweep(terminé, 1f), 0f)
        assertEquals(90f, revealedSweep(abandonné, 1f), 0f)
    }

    @Test
    fun `le balayage revele les tranches dans l'ordre`() {
        // 180° balayés sur 360° : la première tranche est à demi dessinée, la suivante pas encore.
        assertEquals(180f, revealedSweep(terminé, 0.5f), 0.001f)
        assertEquals(0f, revealedSweep(abandonné, 0.5f), 0f)
        // 0,875 × 360° = 315° : la seconde tranche (270° à 360°) est entamée de 45°.
        assertEquals(45f, revealedSweep(abandonné, 0.875f), 0.001f)
    }
}
