package fr.cklla.cartouche

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cklla.cartouche.data.local.AppDatabase
import fr.cklla.cartouche.data.local.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration de la base sur un vrai SQLite : une base en version 9 avec un jeu doit s'ouvrir en
 * version 10 sans rien perdre, avec la file d'envoi vide et prête à servir.
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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
