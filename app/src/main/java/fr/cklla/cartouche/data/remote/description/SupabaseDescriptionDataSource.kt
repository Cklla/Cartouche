package fr.cklla.cartouche.data.remote.description

import fr.cklla.cartouche.domain.description.DescriptionContent
import fr.cklla.cartouche.domain.description.DescriptionRequestResult
import fr.cklla.cartouche.domain.description.StoredDescription
import fr.cklla.cartouche.domain.description.WriteOutcome
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Description des jeux côté serveur : la table `cartouche.game_description` (PostgREST, sous RLS) et la
 * fonction `cartouche-igdb-description`. Comme pour les jeux, le schéma est passé à chaque appel et
 * aucune requête ne filtre sur `user_id` : le RLS limite l'accès au compte connecté.
 *
 * Aucune clé n'est connue de l'app : la fonction lit elle-même le texte d'IGDB et appelle le service de
 * traduction avec un secret qui reste côté Supabase ; l'app n'envoie que l'id IGDB, avec la session de
 * l'utilisateur. Ni le corps de la réponse ni l'en-tête d'autorisation ne sont journalisés ici.
 */
class SupabaseDescriptionDataSource @Inject constructor(
    private val client: SupabaseClient,
) : RemoteDescriptionDataSource {

    // Les erreurs de lecture remontent : la fiche ne demande alors rien (la ligne existe peut-être).
    override suspend fun read(gameId: String): StoredDescription {
        requireSession()
        val rows = client.postgrest.from(SCHEMA, TABLE).select(Columns.list(*COLUMNS)) {
            filter { eq("game_id", gameId) }
        }.decodeList<DescriptionRow>()
        return when {
            rows.isEmpty() -> StoredDescription.Missing
            rows.size > 1 -> error("Lecture de $TABLE : plusieurs lignes")
            else -> rows.single().toStored()
        }
    }

    override suspend fun request(igdbId: Long): DescriptionRequestResult = try {
        requireSession()
        // Le client n'envoie que l'id : la fonction refuse tout autre champ et lit le texte elle-même.
        val response = client.functions.invoke(FUNCTION_NAME) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("igdbId", igdbId) })
            // Une traduction peut dépasser le délai par défaut du client (10 s) ; même borne que le site.
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
        }
        responseJson.decodeFromString<DescriptionFunctionResponse>(response.bodyAsText()).toResult(igdbId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Réseau, délai, 4xx/5xx de la fonction, corps illisible : une panne. Rien n'est écrit, pas
        // d'autre essai dans cet appel, et le message n'est pas relayé.
        DescriptionRequestResult.Failed
    }

    override suspend fun insert(gameId: String, content: DescriptionContent): WriteOutcome {
        val row = content.toRowOrNull(gameId) ?: return WriteOutcome.FAILED
        return writeOutcome { client.postgrest.from(SCHEMA, TABLE).insert(row) }
    }

    override suspend fun replace(gameId: String, content: DescriptionContent): WriteOutcome {
        val patch = content.toPatchOrNull() ?: return WriteOutcome.FAILED
        var touched = 0
        val outcome = writeOutcome {
            // `select()` renvoie les lignes modifiées : zéro ligne (jeu ou ligne disparus entre-temps,
            // ou RLS) n'est pas un succès.
            touched = client.postgrest.from(SCHEMA, TABLE).update(patch) {
                filter { eq("game_id", gameId) }
                select(Columns.list("game_id"))
            }.decodeList<TouchedRow>().size
        }
        return replaceOutcome(outcome, touched)
    }

    // Tri des codes d'erreur : voir `writeOutcomeOfCode`.
    private suspend fun writeOutcome(block: suspend () -> Unit): WriteOutcome = try {
        requireSession()
        block()
        WriteOutcome.WRITTEN
    } catch (e: CancellationException) {
        throw e
    } catch (e: PostgrestRestException) {
        writeOutcomeOfCode(e.code)
    } catch (e: Exception) {
        WriteOutcome.FAILED
    }

    // Sans session, le SDK enverrait la requête avec la seule clé publique : le RLS ne laisserait voir
    // aucune ligne, sans erreur, et la fiche croirait la description absente (donc la redemanderait).
    private fun requireSession() {
        checkNotNull(client.auth.currentSessionOrNull()) { "Aucune session : requête non envoyée" }
    }

    @Serializable
    private data class TouchedRow(@SerialName("game_id") val gameId: String)

    private companion object {
        const val SCHEMA = "cartouche"
        const val TABLE = "game_description"
        const val FUNCTION_NAME = "cartouche-igdb-description"
        const val REQUEST_TIMEOUT_MILLIS = 60_000L
        val COLUMNS = arrayOf("game_id", "igdb_id", "summary", "summary_fr", "translation_model")
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
