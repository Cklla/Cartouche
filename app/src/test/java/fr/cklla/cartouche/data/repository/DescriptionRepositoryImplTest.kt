package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.entity.DescriptionEntity
import fr.cklla.cartouche.data.remote.description.RemoteDescriptionDataSource
import fr.cklla.cartouche.domain.description.DescriptionContent
import fr.cklla.cartouche.domain.description.DescriptionRequestResult
import fr.cklla.cartouche.domain.description.DescriptionView
import fr.cklla.cartouche.domain.description.GameDescription
import fr.cklla.cartouche.domain.description.StoredDescription
import fr.cklla.cartouche.domain.description.WriteOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le serveur reste la source de vérité ; Room n'est qu'un cache de lecture (hors ligne). */
class DescriptionRepositoryImplTest {

    private val igdbId = 113112L
    private val content = DescriptionContent(igdbId, "Original", "Traduction", "m")

    private class FakeRemote : RemoteDescriptionDataSource {
        var stored: () -> StoredDescription = { StoredDescription.Missing }
        var request: DescriptionRequestResult = DescriptionRequestResult.Failed
        var insertOutcome = WriteOutcome.WRITTEN
        var replaceOutcome = WriteOutcome.WRITTEN
        val log = mutableListOf<String>()

        override suspend fun read(gameId: String): StoredDescription {
            log += "read"
            return stored()
        }

        override suspend fun request(igdbId: Long): DescriptionRequestResult {
            log += "request"
            return request
        }

        override suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome {
            log += "insert"
            return insertOutcome
        }

        override suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome {
            log += "replace"
            return replaceOutcome
        }
    }

    private val remote = FakeRemote()
    private val dao = FakeDescriptionDao()
    // Les faux ne suspendent que sur les portes des tests : `Unconfined` rend l'exécution déterministe.
    private val repository = DescriptionRepositoryImpl(remote, dao, CoroutineScope(Dispatchers.Unconfined))

    private fun entity(c: DescriptionContent = content) = GameDescription("g1", c).let {
        DescriptionEntity("g1", c.igdbId, c.summary, c.summaryFr, c.translationModel)
    }

    @Test
    fun `une description lue sur le serveur entre dans le cache`() = runTest {
        remote.stored = { StoredDescription.Present(GameDescription("g1", content)) }

        val view = repository.resolve("g1", igdbId)

        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), view)
        assertEquals(entity(), dao.rows["g1"])
        assertEquals(listOf("read"), remote.log)
    }

    @Test
    fun `hors ligne, la description deja en cache est servie sans demander de traduction`() = runTest {
        dao.put(entity())
        remote.stored = { error("hors ligne") }

        val view = repository.resolve("g1", igdbId)

        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), view)
        assertEquals(listOf("read"), remote.log)
    }

    @Test
    fun `hors ligne sans cache, la fiche reste sans description et ne demande rien`() = runTest {
        remote.stored = { error("hors ligne") }

        assertEquals(DescriptionView.Failed, repository.resolve("g1", igdbId))
        assertEquals(listOf("read"), remote.log)
    }

    @Test
    fun `hors ligne, un cache perime (autre id IGDB) n est jamais servi`() = runTest {
        dao.put(entity(content.copy(igdbId = 999)))
        remote.stored = { error("hors ligne") }
        // Si la copie périmée était servie, la fiche croirait la ligne lue et demanderait une traduction.
        remote.request = DescriptionRequestResult.Found(content)

        assertEquals(DescriptionView.Failed, repository.resolve("g1", igdbId))
        assertEquals(listOf("read"), remote.log)
        assertEquals(999L, dao.rows["g1"]?.igdbId)
    }

    @Test
    fun `une ligne supprimee sur le serveur vide le cache, puis la fiche redemande`() = runTest {
        dao.put(entity())
        remote.stored = { StoredDescription.Missing }
        remote.request = DescriptionRequestResult.Found(content.copy(summaryFr = "Nouvelle traduction"))

        val view = repository.resolve("g1", igdbId)

        assertEquals(DescriptionView.Ready("Original", "Nouvelle traduction", saved = true), view)
        assertEquals(listOf("read", "request", "insert"), remote.log)
        assertEquals("Nouvelle traduction", dao.rows["g1"]?.summaryFr)
    }

    @Test
    fun `un texte ecrit sur le serveur entre dans le cache, un texte non enregistre n y entre pas`() = runTest {
        remote.request = DescriptionRequestResult.Found(content)
        remote.insertOutcome = WriteOutcome.FAILED

        val failed = repository.resolve("g1", igdbId) as DescriptionView.Ready
        assertTrue(!failed.saved)
        assertNull(dao.rows["g1"])

        remote.insertOutcome = WriteOutcome.WRITTEN
        repository.resolve("g1", igdbId)
        assertEquals(entity(), dao.rows["g1"])
    }

    @Test
    fun `pas de description est mis en cache aussi, pour ne plus rien demander hors ligne`() = runTest {
        remote.request = DescriptionRequestResult.NotFound(DescriptionContent(igdbId, null, null, null))

        assertEquals(DescriptionView.None, repository.resolve("g1", igdbId))
        assertEquals(DescriptionEntity("g1", igdbId, null, null, null), dao.rows["g1"])
    }

    @Test
    fun `une panne de la fonction n ecrit rien, ni sur le serveur ni dans le cache`() = runTest {
        remote.request = DescriptionRequestResult.Failed

        assertEquals(DescriptionView.Failed, repository.resolve("g1", igdbId))
        assertEquals(listOf("read", "request"), remote.log)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `le nettoyage des orphelines retire les descriptions des jeux disparus`() = runTest {
        dao.knownGameIds = setOf("g1")
        dao.put(entity())
        dao.put(entity().copy(gameId = "disparu"))
        remote.stored = { StoredDescription.Present(GameDescription("g1", content)) }

        repository.peek("g1", igdbId)

        assertEquals(setOf("g1"), dao.rows.keys)
    }

    @Test
    fun `un echec du nettoyage n empeche pas d afficher la fiche`() = runTest {
        dao.failOnDeleteOrphans = true
        remote.stored = { StoredDescription.Present(GameDescription("g1", content)) }

        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), repository.peek("g1", igdbId))
    }

    @Test
    fun `un jeu sans id IGDB n appelle rien`() = runTest {
        assertEquals(DescriptionView.None, repository.resolve("g1", null))
        assertTrue(remote.log.isEmpty())
    }

    @Test
    fun `une ligne illisible sur le serveur vide la copie locale, elle n est pas servie`() = runTest {
        dao.put(entity())
        remote.stored = { StoredDescription.Unreadable }
        remote.request = DescriptionRequestResult.Failed

        assertEquals(DescriptionView.Failed, repository.resolve("g1", igdbId))
        assertNull(dao.rows["g1"])
    }

    @Test
    fun `une erreur Room ne fait pas passer pour une panne une description que le serveur a`() = runTest {
        dao.failOnPut = true
        remote.stored = { StoredDescription.Present(GameDescription("g1", content)) }

        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), repository.resolve("g1", igdbId))
    }

    @Test
    fun `quitter la fiche pendant la demande n annule pas la traduction, elle est enregistree`() = runTest {
        val gate = CompletableDeferred<DescriptionRequestResult>()
        val gated = object : RemoteDescriptionDataSource by remote {
            override suspend fun request(igdbId: Long): DescriptionRequestResult {
                remote.log += "request"
                return gate.await()
            }
        }
        val gatedRepository = DescriptionRepositoryImpl(gated, dao, CoroutineScope(Dispatchers.Unconfined))

        val caller = launch(Dispatchers.Unconfined) { gatedRepository.resolve("g1", igdbId) }
        assertEquals(listOf("read", "request"), remote.log)
        // L'utilisateur quitte la fiche : l'attente de l'appelant est annulée, pas la demande.
        caller.cancelAndJoin()
        gate.complete(DescriptionRequestResult.Found(content))

        assertEquals(listOf("read", "request", "insert"), remote.log)
        assertEquals(entity(), dao.rows["g1"])
    }

    @Test
    fun `lecture seule, une ligne a jour est lue et mise en cache, la fonction n est jamais appelee`() = runTest {
        remote.stored = { StoredDescription.Present(GameDescription("g1", content)) }

        val view = repository.peek("g1", igdbId)

        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), view)
        assertEquals(entity(), dao.rows["g1"])
        assertEquals(listOf("read"), remote.log)
    }

    @Test
    fun `lecture seule, aucune ligne, section repliee sans demande ni ecriture`() = runTest {
        remote.stored = { StoredDescription.Missing }
        remote.request = DescriptionRequestResult.Found(content)

        val view = repository.peek("g1", igdbId)

        assertEquals(DescriptionView.Collapsed, view)
        assertEquals(listOf("read"), remote.log)
        assertNull(dao.rows["g1"])
    }

    @Test
    fun `lecture seule, hors ligne, le cache a jour est servi, sinon la section reste repliee`() = runTest {
        remote.stored = { error("hors ligne") }
        assertEquals(DescriptionView.Collapsed, repository.peek("g1", igdbId))

        dao.put(entity())
        assertEquals(DescriptionView.Ready("Original", "Traduction", saved = true), repository.peek("g1", igdbId))
        assertTrue("request" !in remote.log)
    }

    @Test
    fun `lecture seule, un jeu sans id IGDB n appelle rien`() = runTest {
        assertEquals(DescriptionView.None, repository.peek("g1", null))
        assertEquals(emptyList<String>(), remote.log)
    }
}
