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
import fr.cklla.cartouche.domain.description.resolveDescription
import fr.cklla.cartouche.domain.repository.DescriptionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Implémentation du [DescriptionRepository] : le serveur reste la source de vérité (une description
 * créée depuis un autre appareil ou depuis le site est relue), Room n'est qu'un cache de lecture qui
 * rend la description disponible hors ligne une fois obtenue.
 *
 * Ce choix « serveur d'abord » évite aussi de servir indéfiniment une ligne que le serveur a supprimée
 * (pour être retraduite, par exemple) : la lecture réussie rafraîchit le cache, et une ligne absente
 * du serveur le vide. Le cache ne sert que quand la lecture serveur échoue.
 */
class DescriptionRepositoryImpl @Inject constructor(
    private val remote: RemoteDescriptionDataSource,
    private val dao: DescriptionDao,
) : DescriptionRepository {

    override suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView =
        try {
            // Nettoyage des descriptions de jeux retirés du backlog : une requête, sur une table de
            // quelques dizaines de lignes. Un échec ici ne doit jamais empêcher d'afficher la fiche.
            runCatching { dao.deleteOrphans() }
            resolveDescription(gameId, igdbId, CachedSource(igdbId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DescriptionView.Failed
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
                val cached = dao.getByGameId(gameId)?.toStored()
                if (cached is StoredDescription.Present && igdbId != null && cached.description.isCurrent(igdbId)) {
                    return cached
                }
                throw e
            }
            when (fromServer) {
                is StoredDescription.Present -> dao.put(fromServer.description.toEntity())
                // Pas de ligne exploitable sur le serveur : une copie locale serait périmée.
                StoredDescription.Missing, StoredDescription.Unreadable -> dao.deleteByGameId(gameId)
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
            if (outcome == WriteOutcome.WRITTEN) dao.put(GameDescription(gameId, content).toEntity())
        }
    }
}
