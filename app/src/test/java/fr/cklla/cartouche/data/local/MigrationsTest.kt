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
 * Vérifie les migrations 9 → 10 et 10 → 11 contre le schéma que Room a exporté pour la version visée : les instructions
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

    // --- 10 -> 11 : colonne igdbId ---

    private fun columnOf(schema: JsonObject, table: String, column: String): JsonObject? =
        entities(schema).map { it.jsonObject }
            .first { it.getValue("tableName").jsonPrimitive.content == table }
            .getValue("fields").jsonArray.map { it.jsonObject }
            .firstOrNull { it.getValue("columnName").jsonPrimitive.content == column }

    @Test
    fun `la migration 10 vers 11 va de la version 10 a la version 11`() {
        assertEquals(10, MIGRATION_10_11.startVersion)
        assertEquals(11, MIGRATION_10_11.endVersion)
    }

    @Test
    fun `la migration 10 vers 11 ajoute exactement la colonne igdbId telle que Room l'attend`() {
        val executed = mutableListOf<String>()

        MIGRATION_10_11.migrate(recordingConnection(executed))

        // Une seule instruction : l'ajout de la colonne, nullable et sans valeur par défaut.
        assertEquals(listOf("ALTER TABLE `games` ADD COLUMN `igdbId` INTEGER"), executed)

        val column = columnOf(exportedSchema(11), "games", "igdbId") ?: error("igdbId absente du schéma 11")
        assertEquals("INTEGER", column.getValue("affinity").jsonPrimitive.content)
        // Nullable (aucune clé `notNull` pour une colonne nullable) et sans valeur par défaut.
        assertTrue("nullable", column["notNull"]?.jsonPrimitive?.content != "true")
        assertTrue("pas de valeur par défaut", !column.containsKey("defaultValue"))
    }

    @Test
    fun `le schema 11 est le schema 10 plus la seule colonne igdbId`() {
        fun columns(version: Int) = entities(exportedSchema(version)).map { it.jsonObject }
            .first { it.getValue("tableName").jsonPrimitive.content == "games" }
            .getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }

        assertEquals(columns(10), columns(11).filter { it != "igdbId" })
        assertEquals(1, columns(11).count { it == "igdbId" })
        // L'autre table n'a pas bougé.
        assertEquals(createSql(exportedSchema(10), "pending_operation"), createSql(exportedSchema(11), "pending_operation"))
    }
}
