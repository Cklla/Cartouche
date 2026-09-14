package fr.cklla.cartouche.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migrations Room de la base `cartouche.db`.
 *
 * Recréation de la table plutôt qu'un simple `ALTER TABLE ... RENAME COLUMN` : ce dernier
 * n'est fiable qu'à partir de SQLite 3.25 (2018), or `minSdk 24` embarque des versions de
 * SQLite plus anciennes sur certains appareils. La table temporaire fonctionne partout.
 *
 * Room 2.8 fait migrer `Migration` vers l'API multiplateforme `SQLiteConnection`
 * (`androidx.sqlite`), qui remplace l'ancien `SupportSQLiteDatabase` propre à Android.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`platform` TEXT NOT NULL, " +
                "`genre` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`userPlaytimeHours` INTEGER NOT NULL, " +
                "`estimatedPlaytimeHours` INTEGER, " +
                "`rating` INTEGER, " +
                "`notes` TEXT NOT NULL, " +
                "`coverUrl` TEXT)",
        )
        connection.execSQL(
            "INSERT INTO `games_new` " +
                "(`id`, `title`, `platform`, `genre`, `status`, `userPlaytimeHours`, " +
                "`estimatedPlaytimeHours`, `rating`, `notes`, `coverUrl`) " +
                "SELECT `id`, `title`, `platform`, `genre`, `status`, `hoursPlayed`, " +
                "NULL, `rating`, `notes`, `coverUrl` FROM `games`",
        )
        connection.execSQL("DROP TABLE `games`")
        connection.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}
