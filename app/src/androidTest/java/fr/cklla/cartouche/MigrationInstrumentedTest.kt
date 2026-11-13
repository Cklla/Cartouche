package fr.cklla.cartouche

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cklla.cartouche.data.local.AppDatabase
import fr.cklla.cartouche.data.local.MIGRATION_10_11
import fr.cklla.cartouche.data.local.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations de la base sur un vrai SQLite : une base en version 9 avec un jeu doit s'ouvrir en
 * version 10 sans rien perdre, avec la file d'envoi vide et prête à servir ; une base en version 10
 * doit s'ouvrir en version 11 avec la colonne `igdbId` à `NULL` pour les jeux existants.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrationDe9a10ConserveLeBacklogEtCreeLaFileDEnvoi() {
        helper.createDatabase(DB_NAME, 9).apply {
            execSQL(
                "INSERT INTO games (id, title, platform, genre, status, userPlaytimeHours, notes, playedPlatforms, igdbLookupAttempted) " +
                    "VALUES ('g1', 'Hades', 'Switch', 'Roguelike', 'EN_COURS', 12, 'bien', 'Switch', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 10, true, MIGRATION_9_10)

        migrated.query("SELECT title, userPlaytimeHours FROM games WHERE id = 'g1'").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("Hades", it.getString(0))
            assertEquals(12, it.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM pending_operation").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.execSQL("INSERT INTO pending_operation (kind, gameId) VALUES ('UPSERT_GAME', 'g1')")
        migrated.query("SELECT seq FROM pending_operation").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrationDe10a11ConserveLeBacklogEtAjouteIgdbIdNul() {
        helper.createDatabase(DB_NAME, 10).apply {
            execSQL(
                "INSERT INTO games (id, title, platform, genre, status, rawgId, userPlaytimeHours, notes, playedPlatforms, igdbLookupAttempted) " +
                    "VALUES ('g1', 'Hades', 'Switch', 'Roguelike', 'EN_COURS', 42, 12, 'bien', 'Switch', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 11, true, MIGRATION_10_11)

        migrated.query("SELECT title, rawgId, userPlaytimeHours, igdbLookupAttempted, igdbId FROM games WHERE id = 'g1'").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("Hades", it.getString(0))
            assertEquals(42L, it.getLong(1))
            assertEquals(12, it.getInt(2))
            assertEquals(1, it.getInt(3))
            assertTrue("igdbId nul pour un jeu existant", it.isNull(4))
        }
        migrated.execSQL("UPDATE games SET igdbId = 113112 WHERE id = 'g1'")
        migrated.query("SELECT igdbId FROM games WHERE id = 'g1'").use {
            it.moveToFirst()
            assertEquals(113112L, it.getLong(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
