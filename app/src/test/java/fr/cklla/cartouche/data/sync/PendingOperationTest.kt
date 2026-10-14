package fr.cklla.cartouche.data.sync

import fr.cklla.cartouche.data.local.entity.PendingOperationEntity
import fr.cklla.cartouche.data.repository.FakeOutboxDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingOperationTest {

    private val outbox = FakeOutboxDao()

    @Test
    fun `la nature d'une operation inconnue vaut null`() {
        assertNull(PendingOperationEntity(kind = "AUTRE", gameId = "g").operationKind)
        assertEquals(PendingOperationKind.DELETE_GAME, PendingOperationEntity(kind = "DELETE_GAME", gameId = "g").operationKind)
    }

    @Test
    fun `les operations gardent leur ordre d'ajout`() = runTest {
        outbox.enqueueUpsert("a")
        outbox.enqueueUpsert("b")
        outbox.enqueueDelete("c")

        assertEquals(listOf("a", "b", "c"), outbox.getAll().map { it.gameId })
    }

    @Test
    fun `enqueueDelete retire les operations en attente du meme jeu seulement`() = runTest {
        outbox.enqueueUpsert("a")
        outbox.enqueueUpsert("a")
        outbox.enqueueUpsert("b")

        outbox.enqueueDelete("a")

        assertEquals(
            listOf("b" to PendingOperationKind.UPSERT_GAME, "a" to PendingOperationKind.DELETE_GAME),
            outbox.getAll().map { it.gameId to it.operationKind },
        )
    }
}
