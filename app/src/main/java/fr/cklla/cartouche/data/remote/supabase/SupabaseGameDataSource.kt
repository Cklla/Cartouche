package fr.cklla.cartouche.data.remote.supabase

import fr.cklla.cartouche.data.remote.PermanentRemoteException
import fr.cklla.cartouche.data.remote.RemoteGameDataSource
import fr.cklla.cartouche.domain.model.Game
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Implémentation [RemoteGameDataSource] sur Supabase (PostgREST pour les lectures et écritures,
 * Realtime pour le signal de changement). La table vit dans le schéma `cartouche`, passé
 * explicitement à chaque appel (le schéma par défaut du client est `public`, celui de Pellicule).
 * L'accès est limité au compte connecté par le RLS côté base : aucune requête ne filtre sur
 * `user_id`.
 */
class SupabaseGameDataSource @Inject constructor(
    private val client: SupabaseClient,
) : RemoteGameDataSource {

    // Aucun filtre sur `user_id` : Realtime n'applique pas un filtre aux suppressions (elles ne
    // portent que la clé primaire), et une suppression faite sur un autre appareil ne serait jamais
    // signalée. Les insertions et modifications restent limitées par le RLS aux lignes du compte ; une
    // suppression étrangère déclenche au pire une lecture inutile.
    override fun changes(): Flow<Unit> = channelFlow {
        val channel = client.channel(CHANNEL_NAME)
        val gameChanges = channel.postgresChangeFlow<PostgresAction>(schema = SCHEMA) { table = TABLE_GAME }
        launch { gameChanges.collect { send(Unit) } }
        // Les événements survenus pendant une coupure ne sont pas rejoués : chaque (re)abonnement
        // déclenche donc une lecture complète.
        launch { channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.collect { send(Unit) } }
        try {
            // Sans délai, un abonnement qui n'aboutit jamais (réseau absent, accès refusé) bloquerait
            // ici sans erreur, et la boucle de synchro ne réessaierait jamais.
            withTimeout(SUBSCRIBE_TIMEOUT_MILLIS) { channel.subscribe(blockUntilSubscribed = true) }
            // Le SDK abandonne un canal après plusieurs erreurs (jeton refusé…), ou le ferme à la
            // demande du serveur, en le repassant simplement à UNSUBSCRIBED. Le flux se termine alors
            // en erreur pour que l'appelant se réabonne, plutôt que de ne plus rien recevoir.
            channel.status.first { it == RealtimeChannel.Status.UNSUBSCRIBED }
            error("Écoute temps réel interrompue")
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    channel.unsubscribe()
                    client.realtime.removeChannel(channel)
                }
            }
        }
    }

    override suspend fun fetchAll(): List<Game> {
        val rows = fetchAllPages(PAGE_SIZE) { from, to ->
            requireSession()
            client.postgrest.from(SCHEMA, TABLE_GAME).select {
                order("id", Order.ASCENDING)
                range(from, to)
            }.decodeList<GameRow>()
        }
        return rows.mapNotNull { it.toGameOrNull() }
    }

    // Sans `onConflict`, PostgREST fusionne sur la clé primaire (`id`) : rejouer l'envoi ne crée pas
    // de doublon.
    override suspend fun upsertGame(game: Game) = write {
        client.postgrest.from(SCHEMA, TABLE_GAME).upsert(game.toRow())
    }

    override suspend fun deleteGame(gameId: String) = write {
        client.postgrest.from(SCHEMA, TABLE_GAME).delete { filter { eq("id", gameId) } }
    }

    // Les erreurs de réseau ou de serveur remontent telles quelles (à réessayer) ; seul un refus
    // définitif du serveur est converti.
    private suspend fun write(block: suspend () -> Unit) {
        requireSession()
        try {
            block()
        } catch (e: PostgrestRestException) {
            if (isPermanentFailure(e.code)) {
                throw PermanentRemoteException("Écriture refusée (${e.statusCode}, ${e.code}) : ${e.description ?: e.error}", e)
            }
            throw e
        }
    }

    // Sans session, le SDK envoie la requête avec la seule clé publique : le RLS ne laisse alors voir
    // aucune ligne, sans erreur. Une lecture renverrait un backlog vide, que le miroir appliquerait en
    // supprimant tout le backlog local ; une suppression « réussirait » sans rien supprimer et sortirait
    // de la file. L'exception levée ici est une erreur ordinaire, réessayée plus tard.
    private fun requireSession() {
        checkNotNull(client.auth.currentSessionOrNull()) { "Aucune session : requête non envoyée" }
    }

    private companion object {
        const val CHANNEL_NAME = "cartouche-backlog"
        const val SCHEMA = "cartouche"
        const val TABLE_GAME = "game"
        const val PAGE_SIZE = 1000
        const val SUBSCRIBE_TIMEOUT_MILLIS = 30_000L
    }
}
