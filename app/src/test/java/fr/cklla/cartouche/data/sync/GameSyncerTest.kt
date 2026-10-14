package fr.cklla.cartouche.data.sync

import fr.cklla.cartouche.data.local.entity.PendingOperationEntity
import fr.cklla.cartouche.data.remote.FakeRemoteGameDataSource
import fr.cklla.cartouche.data.remote.PermanentRemoteException
import fr.cklla.cartouche.data.repository.FakeAuthRepository
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.FakeInitialUploadStore
import fr.cklla.cartouche.data.repository.FakeOutboxDao
import fr.cklla.cartouche.data.repository.fakeSyncer
import fr.cklla.cartouche.data.repository.toEntity
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSyncerTest {

    private val remote = FakeRemoteGameDataSource()
    private val gameDao = FakeGameDao()
    private val outbox = FakeOutboxDao()
    private val auth = FakeAuthRepository()
    private val initialUpload = FakeInitialUploadStore()
    private val syncer = fakeSyncer(remote, outbox, gameDao, auth, initialUpload = initialUpload)

    private val hades = Game(id = "hades", title = "Hades", platform = "Switch", genre = "Roguelike", status = GameStatus.EN_COURS)
    private val celeste = Game(id = "celeste", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.A_FAIRE)

    private suspend fun storeLocally(game: Game) = gameDao.insert(game.toEntity())

    private suspend fun enqueue(kind: PendingOperationKind, gameId: String) {
        when (kind) {
            PendingOperationKind.UPSERT_GAME -> outbox.enqueueUpsert(gameId)
            PendingOperationKind.DELETE_GAME -> outbox.enqueueDelete(gameId)
        }
    }

    // --- flush ---

    @Test
    fun `flush envoie l'etat le plus recent du jeu et non celui de l'ecriture`() = runTest {
        storeLocally(hades)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        gameDao.update(hades.copy(status = GameStatus.TERMINE, rating = 5).toEntity())

        syncer.flush()

        assertEquals(GameStatus.TERMINE, remote.upsertedGames.single().status)
        assertEquals(5, remote.upsertedGames.single().rating)
    }

    @Test
    fun `flush rejoue les operations dans l'ordre de la file`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        enqueue(PendingOperationKind.DELETE_GAME, "autre")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("upsert:hades", "upsert:celeste", "delete:autre"), remote.writeLog)
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une erreur reseau arrete l'envoi et conserve toute la file`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        remote.offline = true

        assertEquals(FlushResult.RETRY_LATER, syncer.flush())

        assertEquals(2, outbox.pending.size)
        assertTrue(remote.writeLog.isEmpty())
    }

    @Test
    fun `une operation ne sort de la file qu'apres confirmation du serveur`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        // Le serveur confirme la première écriture puis tombe.
        remote.writesBeforeFailure = 1

        assertEquals(FlushResult.RETRY_LATER, syncer.flush())

        assertEquals(listOf("hades"), remote.games.value.map { it.id })
        assertEquals(listOf("celeste"), outbox.pending.map { it.gameId })
    }

    @Test
    fun `l'envoi reprend a la premiere operation restante apres une erreur`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        remote.writeFailure = java.io.IOException("serveur 503")
        assertEquals(FlushResult.RETRY_LATER, syncer.flush())
        remote.writeFailure = null

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(setOf("hades", "celeste"), remote.games.value.map { it.id }.toSet())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une file entierement refusee definitivement se vide sans boucle infinie`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        remote.writeFailure = PermanentRemoteException("contrainte violée")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertTrue(outbox.pending.isEmpty())
        assertTrue(remote.games.value.isEmpty())
    }

    @Test
    fun `un refus definitif d'une operation laisse partir la suivante`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        // Le serveur refuse la première écriture seulement.
        remote.writeFailureFor = mapOf("hades" to PermanentRemoteException("contrainte violée"))

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("celeste"), remote.games.value.map { it.id })
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `rejouer deux fois les memes operations ne cree ni doublon ni erreur`() = runTest {
        storeLocally(hades)
        repeat(2) { enqueue(PendingOperationKind.UPSERT_GAME, "hades") }

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(1, remote.games.value.size)
    }

    @Test
    fun `supprimer un jeu absent du serveur ne fait pas d'erreur`() = runTest {
        enqueue(PendingOperationKind.DELETE_GAME, "jamais-envoye")

        assertEquals(FlushResult.DONE, syncer.flush())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `un jeu supprime localement depuis n'envoie rien et sort de la file`() = runTest {
        enqueue(PendingOperationKind.UPSERT_GAME, "disparu")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertTrue(remote.upsertedGames.isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `une operation de nature inconnue est abandonnee sans bloquer la file`() = runTest {
        storeLocally(hades)
        outbox.insert(PendingOperationEntity(kind = "NATURE_FUTURE", gameId = "hades"))
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("hades"), remote.games.value.map { it.id })
    }

    @Test
    fun `sans compte connecte rien n'est envoye et rien n'est abandonne`() = runTest {
        val signedOut = FakeAuthRepository(user = null)
        val syncerSignedOut = fakeSyncer(remote, outbox, gameDao, signedOut)
        storeLocally(hades)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")

        assertEquals(FlushResult.RETRY_LATER, syncerSignedOut.flush())

        assertTrue(remote.writeLog.isEmpty())
        assertEquals(1, outbox.pending.size)
    }

    @Test
    fun `l'envoi attend la fin du chargement de la session au lancement`() = runTest {
        val loading = FakeAuthRepository().apply { startInitializing() }
        val syncerLoading = fakeSyncer(remote, outbox, gameDao, loading)
        storeLocally(hades)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")

        val result = async { syncerLoading.flush() }
        runCurrent()
        assertFalse(result.isCompleted)
        assertTrue(remote.writeLog.isEmpty())
        loading.signInAs(AuthUser(uid = "u", displayName = "Joueur"))
        advanceUntilIdle()

        assertEquals(FlushResult.DONE, result.await())
        assertEquals(listOf("hades"), remote.games.value.map { it.id })
    }

    @Test
    fun `un envoi simultane ne rejoue pas deux fois la meme operation`() = runTest {
        storeLocally(hades)
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")

        val first = async { syncer.flush() }
        val second = async { syncer.flush() }

        assertEquals(FlushResult.DONE, first.await())
        assertEquals(FlushResult.DONE, second.await())
        assertEquals(1, remote.upsertedGames.size)
    }

    // --- miroir ---

    @Test
    fun `le miroir ne reecrit pas un jeu ayant une modification en attente`() = runTest {
        storeLocally(hades.copy(status = GameStatus.TERMINE))
        enqueue(PendingOperationKind.UPSERT_GAME, "hades")
        remote.games.value = listOf(hades)

        assertTrue(syncer.mirror())

        assertEquals(GameStatus.TERMINE.name, gameDao.getByIdOnce("hades")?.status)
    }

    @Test
    fun `le miroir ne supprime pas un jeu qui attend d'etre cree`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")

        assertTrue(syncer.mirror())

        assertEquals(listOf("celeste"), gameDao.getAllIds())
    }

    @Test
    fun `le miroir ne recree pas un jeu dont la suppression est en attente`() = runTest {
        enqueue(PendingOperationKind.DELETE_GAME, "hades")
        remote.games.value = listOf(hades)

        assertTrue(syncer.mirror())

        assertTrue(gameDao.getAllIds().isEmpty())
    }

    @Test
    fun `le miroir applique les ajouts, modifications et suppressions du serveur`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        remote.games.value = listOf(hades.copy(status = GameStatus.TERMINE), Game(id = "nouveau", title = "Nouveau", platform = "PC", genre = "RPG", status = GameStatus.A_FAIRE))

        assertTrue(syncer.mirror())

        assertEquals(setOf("hades", "nouveau"), gameDao.getAllIds().toSet())
        assertEquals(GameStatus.TERMINE.name, gameDao.getByIdOnce("hades")?.status)
    }

    @Test
    fun `une lecture qui echoue ne modifie rien`() = runTest {
        storeLocally(celeste)
        remote.offline = true

        val failure = runCatching { syncer.mirror() }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals(listOf("celeste"), gameDao.getAllIds())
    }

    @Test
    fun `un etat lu avant un envoi reussi est ignore puis relu`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        var armed = true
        // Le serveur est vide au moment de la lecture ; l'envoi aboutit pendant celle-ci.
        remote.onFetch = {
            if (armed) {
                armed = false
                syncer.flush()
            }
        }

        assertTrue(syncer.mirror())

        assertEquals(2, remote.fetchCount)
        assertEquals(listOf("celeste"), gameDao.getAllIds())
    }

    @Test
    fun `le miroir abandonne apres plusieurs lectures toutes rendues perimees`() = runTest {
        storeLocally(celeste)
        // Chaque lecture voit une nouvelle opération partir pendant qu'elle est en cours.
        remote.onFetch = {
            enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
            syncer.flush()
        }

        assertFalse(syncer.mirror())

        assertEquals(3, remote.fetchCount)
        assertEquals(listOf("celeste"), gameDao.getAllIds())
    }

    // --- premier lancement et déconnexion ---

    @Test
    fun `la mise en file initiale envoie les jeux locaux que le serveur n'a pas`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)

        assertTrue(syncer.enqueueLocalOnFirstSync())
        syncer.flush()

        assertEquals(setOf("upsert:hades", "upsert:celeste"), remote.writeLog.toSet())
        assertEquals(setOf("hades", "celeste"), remote.games.value.map { it.id }.toSet())
    }

    @Test
    fun `la mise en file initiale rejouee avant le marquage ne met rien en double`() = runTest {
        storeLocally(hades)
        assertTrue(syncer.enqueueLocalOnFirstSync())
        val firstPass = outbox.pending.toList()

        // Arrêt du processus entre la mise en file et le marquage : la tentative suivante repart de zéro.
        initialUpload.clear()
        assertFalse(syncer.enqueueLocalOnFirstSync())

        assertEquals(firstPass, outbox.pending)
    }

    @Test
    fun `un serveur deja partiellement peuple recoit seulement les jeux qui lui manquent`() = runTest {
        storeLocally(hades)
        storeLocally(celeste)
        remote.games.value = listOf(hades)

        assertTrue(syncer.enqueueLocalOnFirstSync())
        syncer.flush()
        syncer.mirror()

        assertEquals(listOf("upsert:celeste"), remote.writeLog)
        assertEquals(setOf("hades", "celeste"), gameDao.getAllIds().toSet())
        assertEquals(setOf("hades", "celeste"), remote.games.value.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun `un serveur deja a jour ne recoit rien mais l'envoi initial est marque fait`() = runTest {
        storeLocally(hades)
        remote.games.value = listOf(hades)

        assertFalse(syncer.enqueueLocalOnFirstSync())

        assertTrue(outbox.pending.isEmpty())
        assertTrue(initialUpload.isDone("fake-uid"))
    }

    @Test
    fun `apres l'envoi initial un jeu supprime ailleurs n'est pas renvoye`() = runTest {
        storeLocally(hades)
        assertTrue(syncer.enqueueLocalOnFirstSync())
        syncer.flush()

        // Supprimé depuis un autre appareil ; ce Room, resté en retard, ne doit pas le ressusciter.
        remote.games.value = emptyList()
        assertFalse(syncer.enqueueLocalOnFirstSync())
        syncer.mirror()

        assertTrue(outbox.pending.isEmpty())
        assertTrue(gameDao.getAllIds().isEmpty())
    }

    @Test
    fun `le backlog local d'un autre compte est purge sans etre envoye au compte courant`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        initialUpload.doneFor = "un-autre-compte"

        assertFalse(syncer.enqueueLocalOnFirstSync())
        syncer.flush()

        assertTrue(gameDao.getAllOnce().isEmpty())
        assertTrue(outbox.pending.isEmpty())
        assertTrue(remote.writeLog.isEmpty())
        assertTrue(initialUpload.isDone("fake-uid"))
    }

    @Test
    fun `flush n'envoie pas une file remplie sous un autre compte`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")
        initialUpload.doneFor = "un-autre-compte"

        assertEquals(FlushResult.RETRY_LATER, syncer.flush())

        assertTrue(remote.writeLog.isEmpty())
        assertEquals(1, outbox.pending.size)
    }

    @Test
    fun `flush envoie normalement avant tout envoi initial`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")

        assertEquals(FlushResult.DONE, syncer.flush())

        assertEquals(listOf("upsert:celeste"), remote.writeLog)
    }

    @Test
    fun `sans compte connecte l'envoi initial ne fait rien et n'est pas marque`() = runTest {
        storeLocally(celeste)
        val signedOut = FakeAuthRepository(user = null)
        val syncerSignedOut = fakeSyncer(remote, outbox, gameDao, signedOut, initialUpload = initialUpload)

        assertFalse(syncerSignedOut.enqueueLocalOnFirstSync())

        assertTrue(outbox.pending.isEmpty())
        assertEquals(null, initialUpload.doneFor)
    }

    @Test
    fun `la mise en file initiale ne fait rien sans backlog local mais est marquee faite`() = runTest {
        assertFalse(syncer.enqueueLocalOnFirstSync())

        assertTrue(outbox.pending.isEmpty())
        assertTrue(initialUpload.isDone("fake-uid"))
    }

    @Test
    fun `la mise en file initiale sur une lecture ratee leve l'erreur et sera retentee`() = runTest {
        storeLocally(celeste)
        remote.offline = true

        val failure = runCatching { syncer.enqueueLocalOnFirstSync() }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertTrue(outbox.pending.isEmpty())
        assertFalse(initialUpload.isDone("fake-uid"))

        remote.offline = false
        assertTrue(syncer.enqueueLocalOnFirstSync())
        assertEquals(listOf("celeste"), outbox.pending.map { it.gameId })
    }

    @Test
    fun `clearLocalData vide le backlog et la file`() = runTest {
        storeLocally(celeste)
        enqueue(PendingOperationKind.UPSERT_GAME, "celeste")

        syncer.clearLocalData()

        assertTrue(gameDao.getAllIds().isEmpty())
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun `clearLocalData oublie l'envoi initial pour que le prochain compte reparte de zero`() = runTest {
        initialUpload.markDone("fake-uid")

        syncer.clearLocalData()

        assertFalse(initialUpload.isDone("fake-uid"))
    }
}
