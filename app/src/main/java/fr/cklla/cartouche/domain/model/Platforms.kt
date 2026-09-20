package fr.cklla.cartouche.domain.model

/**
 * `Game.platform` est une seule chaîne listant toutes les plateformes RAWG où le jeu est
 * disponible (ex. "PC/PS5/Switch", voir `RawgMappers.formatPlatforms`) — jamais un vrai
 * `List<String>` de bout en bout, pour ne pas complexifier Room/Firestore rien que pour ce champ
 * en lecture seule. Ces fonctions la découpent au seul endroit où le détail par plateforme compte
 * réellement : les cases à cocher "joué sur" de la fiche Détail et les statistiques par plateforme.
 */
private const val PLATFORM_SEPARATOR = "/"

/** Plateformes disponibles pour un jeu, dans l'ordre RAWG d'origine, sans doublons ni chaîne vide. */
fun parsePlatforms(platform: String): List<String> =
    platform.split(PLATFORM_SEPARATOR).map(String::trim).filter(String::isNotBlank).distinct()

/**
 * Plateforme(s) sur laquelle/lesquelles [game] compte réellement pour les statistiques par
 * plateforme (voir `StatsCalculations`). Quand un jeu n'a qu'une seule plateforme disponible, c'est
 * forcément celle-là qui a été jouée — aucune case à cocher n'existe dans ce cas (voir
 * `DetailScreen`) — donc on ne se fie pas à [Game.playedPlatforms] tel quel : il peut être vide pour
 * un jeu mono-plateforme déjà en backlog avant l'ajout de ce champ et pas encore resynchronisé
 * (Firestore reste la source de vérité et écrase le backfill local à chaque synchro tant que le
 * document distant n'a pas été réécrit, voir `GameRepositoryImpl.mirrorIntoRoom`). Pour un jeu
 * multi-plateformes, en revanche, impossible de deviner : on s'en tient à ce que l'utilisateur a
 * explicitement coché.
 */
fun effectivePlayedPlatforms(game: Game): Set<String> {
    val platforms = parsePlatforms(game.platform)
    return if (platforms.size == 1) platforms.toSet() else game.playedPlatforms
}
