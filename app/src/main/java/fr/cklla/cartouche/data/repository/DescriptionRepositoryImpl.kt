package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.DescriptionDao
import fr.cklla.cartouche.data.remote.description.RemoteDescriptionDataSource
import fr.cklla.cartouche.data.remote.description.toEntity
import fr.cklla.cartouche.data.remote.description.toStored
import fr.cklla.cartouche.domain.description.DescriptionContent
import fr.cklla.cartouche.domain.description.DescriptionRequestResult
import fr.cklla.cartouche.domain.description.DescriptionSource
import fr.cklla.cartouche.domain.description.DescriptionView
import fr.cklla.cartouche.domain.description.GameDescription
import fr.cklla.cartouche.domain.description.StoredDescription
import fr.cklla.cartouche.domain.description.WriteOutcome
import fr.cklla.cartouche.domain.description.peekDescription
import fr.cklla.cartouche.domain.description.resolveDescription
import fr.cklla.cartouche.domain.repository.DescriptionRepository
import fr.cklla.cartouche.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Implémentation du [DescriptionRepository] : le serveur reste la source de vérité (une description
 * créée depuis un autre appareil ou depuis le site est relue), Room n'est qu'un cache de lecture qui
 * rend la description disponible hors ligne une fois obtenue.
 *
 * Ce choix « serveur d'abord » évite aussi de servir indéfiniment une ligne que le serveur a supprimée
 * (pour être retraduite, par exemple) : la lecture réussie rafraîchit le cache, et une ligne absente
 * du serveur le vide. Le cache ne sert que quand la lecture serveur échoue.
 *
 * La lecture seule ([peek]) tourne dans la portée de l'appelant : elle ne dépense rien, et la quitter l'annule
 * sans conséquence.
 *
 * La résolution tourne dans la portée de l'application et non dans celle de l'appelant : une traduction
 * est payée dès que la fonction répond, et quitter la fiche (ou replier la section) en cours de route ne doit pas la jeter avant
 * son enregistrement (elle serait refaite, donc repayée, au prochain déroulement). L'appelant qui s'en va
 * n'annule que son attente.
 */
class DescriptionRepositoryImpl @Inject constructor(
    private val remote: RemoteDescriptionDataSource,
    private val dao: DescriptionDao,
    @ApplicationScope private val scope: CoroutineScope,
) : DescriptionRepository {

    override suspend fun peek(gameId: String, igdbId: Long?): DescriptionView =
        try {
            peekDescription(gameId, igdbId, CachedSource(igdbId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DescriptionView.Collapsed
        }

    override suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView =
        scope.async {
            try {
                // Nettoyage des descriptions de jeux retirés du backlog : une requête, sur une table de
                // quelques dizaines de lignes.
                cacheOperation { dao.deleteOrphans() }
                resolveDescription(gameId, igdbId, CachedSource(igdbId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DescriptionView.Failed
            }
        }.await()

    // Le cache est un confort : une erreur Room ne doit jamais faire passer pour une panne une description
    // que le serveur a (ni empêcher d'afficher la fiche). L'annulation, elle, n'est jamais avalée.
    private suspend fun cacheOperation(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // cache indisponible : on s'en passe
        }
    }

    private suspend fun <T> runCachedRead(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Serveur d'abord ; le cache n'intervient qu'en lecture, quand le serveur est injoignable. */
    private inner class CachedSource(private val igdbId: Long?) : DescriptionSource {

        override suspend fun read(gameId: String): StoredDescription {
            val fromServer = try {
                remote.read(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Hors ligne ou serveur en panne : le cache, s'il a une description à jour, la sert.
                // Sinon l'erreur remonte, et la fiche ne demande rien (la ligne existe peut-être).
                val cached = runCachedRead { dao.getByGameId(gameId)?.toStored() }
                if (cached is StoredDescription.Present && igdbId != null && cached.description.isCurrent(igdbId)) {
                    return cached
                }
                throw e
            }
            when (fromServer) {
                is StoredDescription.Present -> cacheOperation { dao.put(fromServer.description.toEntity()) }
                // Pas de ligne exploitable sur le serveur : une copie locale serait périmée.
                StoredDescription.Missing, StoredDescription.Unreadable -> cacheOperation { dao.deleteByGameId(gameId) }
            }
            return fromServer
        }

        override suspend fun request(igdbId: Long): DescriptionRequestResult = remote.request(igdbId)

        override suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome =
            remote.insert(gameId, content).also { cacheIfWritten(it, gameId, content) }

        override suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome =
            remote.replace(gameId, content).also { cacheIfWritten(it, gameId, content) }

        // Seul ce qui est réellement sur le serveur entre dans le cache : un texte « non enregistré »
        // doit être redemandé à la prochaine ouverture, pas servi comme s'il était acquis.
        private suspend fun cacheIfWritten(outcome: WriteOutcome, gameId: String, content: DescriptionContent) {
            if (outcome == WriteOutcome.WRITTEN) cacheOperation { dao.put(GameDescription(gameId, content).toEntity()) }
        }
    }
}
