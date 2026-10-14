package fr.cklla.cartouche.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la migration 9 → 10 contre le schéma que Room a exporté pour la version 10 : les instructions
 * exécutées doivent produire exactement la table attendue par la base, faute de quoi Room refuse
 * d'ouvrir la base au lancement. Ce test JVM complète `MigrationInstrumentedTest`, qui joue la
 * migration sur un vrai SQLite (à lancer sur émulateur).
 */
class MigrationsTest {

    /** Connexion factice qui enregistre le SQL qu'on lui demande d'exécuter. */
    private fun recordingConnection(executed: MutableList<String>): SQLiteConnection {
        val statement = Proxy.newProxyInstance(
            SQLiteStatement::class.java.classLoader,
            arrayOf(SQLiteStatement::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Double::class.javaPrimitiveType -> 0.0
                String::class.java -> ""
                else -> null
            }
        } as SQLiteStatement
        return Proxy.newProxyInstance(
            SQLiteConnection::class.java.classLoader,
            arrayOf(SQLiteConnection::class.java),
        ) { _, method, args ->
            if (method.name == "prepare") {
                executed += args[0] as String
                statement
            } else {
                null
            }
        } as SQLiteConnection
    }

    private fun exportedSchema(version: Int): JsonObject {
        val file = File("schemas/fr.cklla.cartouche.data.local.AppDatabase/$version.json")
        assertTrue("schéma exporté introuvable : ${file.absolutePath}", file.exists())
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun entities(schema: JsonObject): JsonArray = schema.getValue("entities").jsonArray

    private fun createSql(schema: JsonObject, table: String): String {
        val entity = entities(schema).map { it.jsonObject }.firstOrNull { it.getValue("tableName").jsonPrimitive.content == table }
            ?: error("table $table absente du schéma")
        return entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
    }

    @Test
    fun `la migration 9 vers 10 cree la table de la file d'envoi telle que Room l'attend`() {
        val executed = mutableListOf<String>()

        MIGRATION_9_10.migrate(recordingConnection(executed))

        assertEquals(listOf(createSql(exportedSchema(10), "pending_operation")), executed)
    }

    @Test
    fun `la migration 9 vers 10 va de la version 9 a la version 10`() {
        assertEquals(9, MIGRATION_9_10.startVersion)
        assertEquals(10, MIGRATION_9_10.endVersion)
    }

    @Test
    fun `la version 10 ne differe de la version 9 que par la file d'envoi`() {
        fun tables(version: Int): Set<String> =
            entities(exportedSchema(version)).map { it.jsonObject.getValue("tableName").jsonPrimitive.content }.toSet()

        assertEquals(setOf("pending_operation"), tables(10) - tables(9))
        assertEquals(emptySet<String>(), tables(9) - tables(10))
    }
}
