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

/**
 * Le temps de jeu estimé RAWG (un seul champ `estimatedPlaytimeHours`) est remplacé par trois
 * champs IGDB indépendants (`hastily`/`normally`/`completely`, voir `Game`). L'ancienne valeur
 * n'est délibérément pas reportée vers l'un des trois nouveaux champs : sa provenance (RAWG) et
 * sa sémantique (moyenne Steam) sont différentes de celles d'IGDB, la reporter aurait affiché une
 * valeur RAWG sous une étiquette IGDB. Les trois champs démarrent donc à `NULL` pour tous les
 * jeux déjà en backlog, ce qui déclenche une seule nouvelle recherche IGDB à la prochaine
 * ouverture de leur fiche détail (voir `DetailViewModel`) — un léger effet de bord accepté plutôt
 * que d'introduire un champ supplémentaire rien que pour distinguer "pas encore cherché" de
 * "cherché, rien trouvé".
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`platform` TEXT NOT NULL, " +
                "`genre` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`userPlaytimeHours` INTEGER NOT NULL, " +
                "`estimatedPlaytimeHastilyHours` INTEGER, " +
                "`estimatedPlaytimeNormallyHours` INTEGER, " +
                "`estimatedPlaytimeCompletelyHours` INTEGER, " +
                "`rating` INTEGER, " +
                "`notes` TEXT NOT NULL, " +
                "`coverUrl` TEXT)",
        )
        connection.execSQL(
            "INSERT INTO `games_new` " +
                "(`id`, `title`, `platform`, `genre`, `status`, `userPlaytimeHours`, " +
                "`estimatedPlaytimeHastilyHours`, `estimatedPlaytimeNormallyHours`, " +
                "`estimatedPlaytimeCompletelyHours`, `rating`, `notes`, `coverUrl`) " +
                "SELECT `id`, `title`, `platform`, `genre`, `status`, `userPlaytimeHours`, " +
                "NULL, NULL, NULL, `rating`, `notes`, `coverUrl` FROM `games`",
        )
        connection.execSQL("DROP TABLE `games`")
        connection.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}

/**
 * Ajoute `rawgId` et `releaseYear`, utilisés uniquement pour fiabiliser la correspondance IGDB
 * (ID Steam puis année de sortie, voir `IgdbPlaytimeRepository`) — jamais affichés. Les jeux déjà
 * en backlog n'ont pas ces informations (elles ne sont connues qu'au moment de la recherche RAWG,
 * voir `GameSearchResult.toGame`) : les deux colonnes démarrent donc à `NULL` pour eux, ce qui fait
 * simplement retomber leur prochaine correspondance IGDB sur le seul nom (comportement identique à
 * avant cette migration), sans effet de bord sur le cache des temps de jeu déjà renseignés.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`platform` TEXT NOT NULL, " +
                "`genre` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`rawgId` INTEGER, " +
                "`releaseYear` INTEGER, " +
                "`userPlaytimeHours` INTEGER NOT NULL, " +
                "`estimatedPlaytimeHastilyHours` INTEGER, " +
                "`estimatedPlaytimeNormallyHours` INTEGER, " +
                "`estimatedPlaytimeCompletelyHours` INTEGER, " +
                "`rating` INTEGER, " +
                "`notes` TEXT NOT NULL, " +
                "`coverUrl` TEXT)",
        )
        connection.execSQL(
            "INSERT INTO `games_new` " +
                "(`id`, `title`, `platform`, `genre`, `status`, `rawgId`, `releaseYear`, " +
                "`userPlaytimeHours`, `estimatedPlaytimeHastilyHours`, `estimatedPlaytimeNormallyHours`, " +
                "`estimatedPlaytimeCompletelyHours`, `rating`, `notes`, `coverUrl`) " +
                "SELECT `id`, `title`, `platform`, `genre`, `status`, NULL, NULL, " +
                "`userPlaytimeHours`, `estimatedPlaytimeHastilyHours`, `estimatedPlaytimeNormallyHours`, " +
                "`estimatedPlaytimeCompletelyHours`, `rating`, `notes`, `coverUrl` FROM `games`",
        )
        connection.execSQL("DROP TABLE `games`")
        connection.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}

/**
 * `id` passe d'un entier auto-incrémenté à un UUID (`TEXT`) : préparation de la synchro
 * Firestore, où l'id d'un jeu doit être stable et unique sur tous les appareils (deux téléphones
 * généreraient tous les deux un jeu n°1 avec un simple compteur local, voir `GameEntity`).
 *
 * SQLite n'a pas de fonction UUID native : `lower(hex(randomblob(4)) || '-' || ...)` génère une
 * chaîne au format UUID (groupes 8-4-4-4-12) à partir d'octets aléatoires. Ce n'est pas un vrai
 * UUIDv4 au sens strict (les bits de version/variant ne sont pas forcés à la bonne valeur), mais
 * l'app n'a besoin que d'un identifiant unique et stable, pas de conformité RFC 4122 — chaque
 * appel à `randomblob()` est réévalué pour chaque ligne copiée par l'`INSERT ... SELECT`, donc
 * chaque jeu reçoit bien un id différent.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` TEXT NOT NULL PRIMARY KEY, " +
                "`title` TEXT NOT NULL, " +
                "`platform` TEXT NOT NULL, " +
                "`genre` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`rawgId` INTEGER, " +
                "`releaseYear` INTEGER, " +
                "`userPlaytimeHours` INTEGER NOT NULL, " +
                "`estimatedPlaytimeHastilyHours` INTEGER, " +
                "`estimatedPlaytimeNormallyHours` INTEGER, " +
                "`estimatedPlaytimeCompletelyHours` INTEGER, " +
                "`rating` INTEGER, " +
                "`notes` TEXT NOT NULL, " +
                "`coverUrl` TEXT)",
        )
        connection.execSQL(
            "INSERT INTO `games_new` " +
                "(`id`, `title`, `platform`, `genre`, `status`, `rawgId`, `releaseYear`, " +
                "`userPlaytimeHours`, `estimatedPlaytimeHastilyHours`, `estimatedPlaytimeNormallyHours`, " +
                "`estimatedPlaytimeCompletelyHours`, `rating`, `notes`, `coverUrl`) " +
                "SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || " +
                "hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6))), " +
                "`title`, `platform`, `genre`, `status`, `rawgId`, `releaseYear`, " +
                "`userPlaytimeHours`, `estimatedPlaytimeHastilyHours`, `estimatedPlaytimeNormallyHours`, " +
                "`estimatedPlaytimeCompletelyHours`, `rating`, `notes`, `coverUrl` FROM `games`",
        )
        connection.execSQL("DROP TABLE `games`")
        connection.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}
