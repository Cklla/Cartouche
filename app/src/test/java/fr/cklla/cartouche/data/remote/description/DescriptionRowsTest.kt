package fr.cklla.cartouche.data.remote.description

import fr.cklla.cartouche.data.local.entity.DescriptionEntity
import fr.cklla.cartouche.domain.description.DescriptionContent
import fr.cklla.cartouche.domain.description.DescriptionRequestResult
import fr.cklla.cartouche.domain.description.GameDescription
import fr.cklla.cartouche.domain.description.StoredDescription
import fr.cklla.cartouche.domain.description.emptyContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DescriptionRowsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(summary: String? = "Original", fr: String? = "Traduction", model: String? = "m") =
        DescriptionContent(113112, summary, fr, model)

    private fun row(summary: String? = "Original", fr: String? = "Traduction", model: String? = "m") =
        DescriptionRow("g1", 113112, summary, fr, model)

    private fun response(text: String) = json.decodeFromString<DescriptionFunctionResponse>(text)

    // --- Lignes lues ---

    @Test
    fun `une ligne dans les bornes est lue, y compris pas de description`() {
        assertEquals(StoredDescription.Present(GameDescription("g1", content())), row().toStored())
        assertEquals(
            StoredDescription.Present(GameDescription("g1", emptyContent(113112))),
            row(null, null, null).toStored(),
        )
    }

    @Test
    fun `une ligne admise par la table mais refusee par ce client est illisible, jamais absente`() {
        // Texte blanc, traduction sans modèle : la table les accepte, ce client non.
        assertEquals(StoredDescription.Unreadable, row(summary = "   ").toStored())
        assertEquals(StoredDescription.Unreadable, row(model = null).toStored())
        assertEquals(StoredDescription.Unreadable, row(summary = null).toStored())
    }

    @Test
    fun `les colonnes en plus sont ignorees et une ligne mal typee leve`() {
        val ok = json.decodeFromString<DescriptionRow>(
            """{"game_id":"g1","igdb_id":1,"summary":"a","summary_fr":null,"translation_model":null,"user_id":"x","created_at":"2026"}""",
        )
        assertEquals("g1", ok.gameId)

        listOf(
            """{"game_id":"g1","igdb_id":1,"summary":5,"summary_fr":null,"translation_model":null}""",
            """{"game_id":"g1","igdb_id":1,"summary":"a","summary_fr":null}""",
        ).forEach {
            try {
                json.decodeFromString<DescriptionRow>(it)
                fail("ligne mal formée acceptée : $it")
            } catch (_: SerializationException) {
                // attendu : erreur de lecture, jamais une description à moitié valide
            }
        }
    }

    // --- Lignes écrites ---

    @Test
    fun `la ligne a inserer n a jamais de user_id ni de colonne hors table`() {
        val encoded = Json.encodeToJsonElement(DescriptionRow.serializer(), content().toRowOrNull("g1")!!).jsonObject
        assertEquals(setOf("game_id", "igdb_id", "summary", "summary_fr", "translation_model"), encoded.keys)
    }

    @Test
    fun `pas de description part avec des null explicites, pour effacer l ancien texte au remplacement`() {
        val patch = Json.encodeToJsonElement(DescriptionPatchRow.serializer(), emptyContent(113112).toPatchOrNull()!!).jsonObject
        assertEquals(setOf("igdb_id", "summary", "summary_fr", "translation_model"), patch.keys)
        assertEquals(JsonNull, patch.getValue("summary"))
        assertEquals(JsonNull, patch.getValue("summary_fr"))
        assertEquals(JsonNull, patch.getValue("translation_model"))
        assertFalse("game_id" in patch)
        assertEquals(JsonPrimitive(113112), patch.getValue("igdb_id"))
    }

    @Test
    fun `rien ne part hors des bornes`() {
        assertNull(content(summary = "a".repeat(5001)).toRowOrNull("g1"))
        assertNull(content(summary = "").toRowOrNull("g1"))
        assertNull(content(model = null).toPatchOrNull())
        assertNull(DescriptionContent(0, "a", "b", "m").toRowOrNull("g1"))
        assertTrue(content().toPatchOrNull() != null)
    }

    // --- Réponse de la fonction ---

    @Test
    fun `une reponse complete est lue et le champ truncated ignore`() {
        val result = response(
            """{"igdbId":113112,"summary":"Original","summaryFr":"Traduction","model":"m","truncated":true}""",
        ).toResult(113112)
        assertEquals(DescriptionRequestResult.Found(content()), result)
    }

    @Test
    fun `IGDB sans description est lu comme introuvable, definitif`() {
        val result = response("""{"igdbId":113112,"summary":null,"summaryFr":null,"model":null}""").toResult(113112)
        assertEquals(DescriptionRequestResult.NotFound(emptyContent(113112)), result)
    }

    @Test
    fun `toute reponse qui n a pas la forme attendue est une panne`() {
        // Mauvais id, original sans traduction, traduction sans modèle, texte vide, texte hors borne.
        listOf(
            """{"igdbId":5,"summary":"a","summaryFr":"b","model":"m"}""",
            """{"igdbId":113112,"summary":"a","summaryFr":null,"model":null}""",
            """{"igdbId":113112,"summary":"a","summaryFr":"b","model":null}""",
            """{"igdbId":113112,"summary":"","summaryFr":"b","model":"m"}""",
            """{"igdbId":113112,"summary":"a","summaryFr":"${"b".repeat(10_001)}","model":"m"}""",
            """{"igdbId":113112,"summary":null,"summaryFr":"b","model":"m"}""",
        ).forEach {
            assertEquals(it.take(60), DescriptionRequestResult.Failed, response(it).toResult(113112))
        }
    }

    @Test
    fun `une reponse mal typee ou incomplete ne se decode pas`() {
        listOf(
            """{"igdbId":113112,"summary":7,"summaryFr":"b","model":"m"}""",
            """{"igdbId":113112,"summary":"a","summaryFr":"b"}""",
            """{"summary":"a","summaryFr":"b","model":"m"}""",
            "[]",
            "pas du json",
        ).forEach {
            try {
                response(it)
                fail("réponse acceptée : $it")
            } catch (_: SerializationException) {
                // attendu : l'appelant traite l'exception comme une panne
            } catch (_: IllegalArgumentException) {
                // idem (JSON invalide)
            }
        }
    }

    // --- Cache Room ---

    @Test
    fun `l entite du cache fait l aller-retour et une entite hors bornes est illisible`() {
        val description = GameDescription("g1", content())
        assertEquals(StoredDescription.Present(description), description.toEntity().toStored())
        assertEquals(
            StoredDescription.Unreadable,
            DescriptionEntity("g1", 1, "  ", "b", "m").toStored(),
        )
    }
}
