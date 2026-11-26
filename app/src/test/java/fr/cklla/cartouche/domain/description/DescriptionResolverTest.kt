package fr.cklla.cartouche.domain.description

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règle de déclenchement de la fiche : mêmes cas que ceux du site, pour que les deux clients se
 * comportent de la même façon face à la même table.
 */
class DescriptionResolverTest {

    private val gameId = "g1"
    private val igdbId = 113112L

    private fun content(id: Long = igdbId, summary: String? = "Original", fr: String? = "Traduction", model: String? = "m") =
        DescriptionContent(id, summary, fr, model)

    private fun stored(c: DescriptionContent = content()) = StoredDescription.Present(GameDescription(gameId, c))

    /** Source scriptée : enregistre chaque appel, répond ce qu'on lui a préparé. */
    private class FakeSource(
        var reads: MutableList<() -> StoredDescription> = mutableListOf(),
        var request: DescriptionRequestResult = DescriptionRequestResult.Failed,
        var insertOutcome: WriteOutcome = WriteOutcome.WRITTEN,
        var replaceOutcome: WriteOutcome = WriteOutcome.WRITTEN,
    ) : DescriptionSource {
        val log = mutableListOf<String>()
        val written = mutableListOf<DescriptionContent>()
        private var readCount = 0

        fun readsReturn(vararg results: StoredDescription) {
            reads = results.map { r -> { r } }.toMutableList()
        }

        override suspend fun read(gameId: String): StoredDescription {
            log += "read"
            val read = reads.getOrElse(minOf(readCount, reads.lastIndex)) { { StoredDescription.Missing } }
            readCount++
            return read()
        }

        override suspend fun request(igdbId: Long): DescriptionRequestResult {
            log += "request"
            return request
        }

        override suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome {
            log += "insert"
            written += content
            return insertOutcome
        }

        override suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome {
            log += "replace"
            written += content
            return replaceOutcome
        }
    }

    private fun source(read: StoredDescription = StoredDescription.Missing, block: FakeSource.() -> Unit = {}) =
        FakeSource().apply { readsReturn(read); block() }

    @Test
    fun `jeu sans id IGDB ou id hors borne, rien n est lu ni demande`() = runTest {
        val s = source()
        assertEquals(DescriptionView.None, resolveDescription(gameId, null, s))
        assertEquals(DescriptionView.None, resolveDescription(gameId, 0L, s))
        assertEquals(DescriptionView.None, resolveDescription(gameId, 10_000_000_000, s))
        assertEquals(emptyList<String>(), s.log)
    }

    @Test
    fun `description enregistree pour cet id, affichee, la fonction n est pas appelee`() = runTest {
        val s = source(stored())
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read"), s.log)
    }

    @Test
    fun `IGDB n a pas de description deja enregistre, pas de section, aucune nouvelle demande`() = runTest {
        val s = source(stored(emptyContent(igdbId)))
        assertEquals(DescriptionView.None, resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read"), s.log)
    }

    @Test
    fun `original sans traduction enregistre, l original est montre, rien n est redemande`() = runTest {
        val s = source(stored(content(fr = null, model = null)))
        assertEquals(DescriptionView.Ready("Original", null, saved = true), resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read"), s.log)
    }

    @Test
    fun `aucune ligne, une demande, puis insert du contenu`() = runTest {
        val s = source { request = DescriptionRequestResult.Found(content()) }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request", "insert"), s.log)
        assertEquals(listOf(content()), s.written)
    }

    @Test
    fun `IGDB sans description, enregistre null pour ne plus demander, aucune section`() = runTest {
        val s = source { request = DescriptionRequestResult.NotFound(emptyContent(igdbId)) }
        assertEquals(DescriptionView.None, resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request", "insert"), s.log)
        assertEquals(listOf(emptyContent(igdbId)), s.written)
        assertEquals(null, s.written.single().summary)
    }

    @Test
    fun `panne de la fonction, rien n est ecrit, l essai n est pas consomme`() = runTest {
        val s = source { request = DescriptionRequestResult.Failed }
        assertEquals(DescriptionView.Failed, resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request"), s.log)
    }

    @Test
    fun `lecture en erreur, rien n est demande car la ligne existe peut-etre, aucune ecriture`() = runTest {
        val s = source { reads = mutableListOf({ error("réseau") }) }
        assertEquals(DescriptionView.Failed, resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read"), s.log)
    }

    @Test
    fun `l annulation n est jamais avalee`() = runTest {
        val s = source { reads = mutableListOf({ throw CancellationException("annulé") }) }
        val result = runCatching { resolveDescription(gameId, igdbId, s) }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `description perimee (autre id IGDB), non affichee, remplacee par update, jamais par insert`() = runTest {
        val s = source(stored(content(id = 999, summary = "Autre jeu", fr = "Autre jeu fr"))) {
            request = DescriptionRequestResult.Found(content())
        }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request", "replace"), s.log)
    }

    @Test
    fun `description perimee et panne de la fonction, la perimee n est pas affichee`() = runTest {
        val s = source(stored(content(id = 999))) { request = DescriptionRequestResult.Failed }
        assertEquals(DescriptionView.Failed, resolveDescription(gameId, igdbId, s))
        assertTrue("replace" !in s.log && "insert" !in s.log)
    }

    @Test
    fun `ligne illisible, traitee comme perimee, reparee par update, jamais par insert`() = runTest {
        val s = source(StoredDescription.Unreadable) { request = DescriptionRequestResult.Found(content()) }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request", "replace"), s.log)
    }

    @Test
    fun `ligne illisible et panne de la fonction, rien n est ecrit`() = runTest {
        val s = source(StoredDescription.Unreadable) { request = DescriptionRequestResult.Failed }
        assertEquals(DescriptionView.Failed, resolveDescription(gameId, igdbId, s))
        assertEquals(listOf("read", "request"), s.log)
    }

    @Test
    fun `conflit a l insert (autre appareil plus rapide), relecture, sans erreur visible`() = runTest {
        val s = FakeSource(
            request = DescriptionRequestResult.Found(content(summary = "Mon original")),
            insertOutcome = WriteOutcome.CONFLICT,
        ).apply { readsReturn(StoredDescription.Missing, stored(content(summary = "Celui de l'autre appareil"))) }
        assertEquals(
            DescriptionView.Ready("Celui de l'autre appareil", "Traduction", saved = true),
            resolveDescription(gameId, igdbId, s),
        )
        assertEquals(listOf("read", "request", "insert", "read"), s.log)
    }

    @Test
    fun `conflit puis relecture impossible ou ligne perimee, le texte obtenu reste affiche, marque non enregistre`() = runTest {
        val unreadable = FakeSource(
            request = DescriptionRequestResult.Found(content()),
            insertOutcome = WriteOutcome.CONFLICT,
        ).apply { reads = mutableListOf({ StoredDescription.Missing }, { error("hors ligne") }) }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = false), resolveDescription(gameId, igdbId, unreadable))

        val stale = FakeSource(
            request = DescriptionRequestResult.Found(content()),
            insertOutcome = WriteOutcome.CONFLICT,
        ).apply { readsReturn(StoredDescription.Missing, stored(content(id = 999))) }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = false), resolveDescription(gameId, igdbId, stale))
    }

    @Test
    fun `ecriture en panne, le texte est affiche, marque non enregistre, jamais avale`() = runTest {
        val s = source { request = DescriptionRequestResult.Found(content()); insertOutcome = WriteOutcome.FAILED }
        val view = resolveDescription(gameId, igdbId, s) as DescriptionView.Ready
        assertFalse(view.saved)
        assertEquals("Traduction", view.summaryFr)
    }

    @Test
    fun `refus de la table (23514), pas de description est ecrit a la place, definitif, sans nouvelle demande`() = runTest {
        val s = source {
            request = DescriptionRequestResult.Found(content())
            insertOutcome = WriteOutcome.REJECTED
        }
        // Le repli passe lui aussi par insert : on le fait réussir au second appel seulement.
        val outcomes = ArrayDeque(listOf(WriteOutcome.REJECTED, WriteOutcome.WRITTEN))
        val scripted = object : DescriptionSource by s {
            override suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome {
                s.insert(gameId, content)
                return outcomes.removeFirst()
            }
        }
        assertEquals(DescriptionView.None, resolveDescription(gameId, igdbId, scripted))
        assertEquals(listOf("read", "request", "insert", "insert"), s.log)
        assertEquals(listOf(content(), emptyContent(igdbId)), s.written)
        assertEquals(1, s.log.count { it == "request" })
    }

    @Test
    fun `refus de la table sur une ligne perimee, le repli passe par update`() = runTest {
        val s = source(stored(content(id = 999))) { request = DescriptionRequestResult.Found(content()) }
        val outcomes = ArrayDeque(listOf(WriteOutcome.REJECTED, WriteOutcome.WRITTEN))
        val scripted = object : DescriptionSource by s {
            override suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome {
                s.replace(gameId, content)
                return outcomes.removeFirst()
            }
        }
        assertEquals(DescriptionView.None, resolveDescription(gameId, igdbId, scripted))
        assertEquals(listOf("read", "request", "replace", "replace"), s.log)
        assertEquals(listOf(content(), emptyContent(igdbId)), s.written)
    }

    @Test
    fun `refus de la table et repli en echec, panne visible, jamais de boucle dans un meme appel`() = runTest {
        val s = source {
            request = DescriptionRequestResult.Found(content())
            insertOutcome = WriteOutcome.REJECTED
        }
        assertEquals(DescriptionView.Failed, resolveDescription(gameId, igdbId, s))
        assertEquals(1, s.log.count { it == "request" })
        assertEquals(2, s.log.count { it == "insert" })
    }

    // --- Lecture seule de l'ouverture de la fiche : jamais de demande, jamais d'écriture ---

    private fun assertReadOnly(source: FakeSource) {
        assertFalse("request" in source.log)
        assertFalse("insert" in source.log || "replace" in source.log)
    }

    @Test
    fun `lecture seule, jeu sans id IGDB ou id hors borne, rien n est lu`() = runTest {
        val source = FakeSource()
        assertEquals(DescriptionView.None, peekDescription(gameId, null, source))
        assertEquals(DescriptionView.None, peekDescription(gameId, 0L, source))
        assertEquals(emptyList<String>(), source.log)
    }

    @Test
    fun `lecture seule, ligne a jour avec texte, prete a etre montree sans demande`() = runTest {
        val source = FakeSource().apply { readsReturn(stored()) }
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), peekDescription(gameId, igdbId, source))
        assertEquals(listOf("read"), source.log)
    }

    @Test
    fun `lecture seule, ligne a jour sans texte, pas de section`() = runTest {
        val source = FakeSource().apply { readsReturn(stored(content(summary = null, fr = null))) }
        assertEquals(DescriptionView.None, peekDescription(gameId, igdbId, source))
        assertReadOnly(source)
    }

    @Test
    fun `lecture seule, ligne absente, perimee ou illisible, section repliee sans demande ni ecriture`() = runTest {
        for (result in listOf(StoredDescription.Missing, StoredDescription.Unreadable, stored(content(id = 7L)))) {
            val source = FakeSource().apply { readsReturn(result) }
            assertEquals(DescriptionView.Collapsed, peekDescription(gameId, igdbId, source))
            assertReadOnly(source)
        }
    }

    @Test
    fun `lecture seule, lecture en erreur, section repliee sans demande`() = runTest {
        val source = FakeSource(reads = mutableListOf({ throw IllegalStateException("réseau") }))
        assertEquals(DescriptionView.Collapsed, peekDescription(gameId, igdbId, source))
        assertReadOnly(source)
    }

    @Test
    fun `lecture seule, l annulation n est jamais avalee`() = runTest {
        val source = FakeSource(reads = mutableListOf({ throw CancellationException("annulé") }))
        try {
            peekDescription(gameId, igdbId, source)
            org.junit.Assert.fail("l'annulation aurait dû remonter")
        } catch (e: CancellationException) {
            // attendu
        }
    }
}
