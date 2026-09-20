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

/**
 * Ré-aligne [playedPlatforms] sur [newPlatform] (typiquement une correction IGDB, voir
 * `IgdbGameMatch.platform`) en cas de remplacement de `Game.platform`, ou `null` si ce remplacement
 * casserait une plateforme déjà cochée — signal à l'appelant (voir `DetailViewModel`) de ne pas
 * remplacer `Game.platform` du tout, pour ne jamais perdre silencieusement un choix déjà fait.
 *
 * La correspondance entre l'ancien et le nouveau nom de plateforme n'est acceptée que si elle est
 * exacte à la casse près (`"ps5"` ~ `"PS5"`), jamais approximative (ex. sous-chaîne) : le cas motivant
 * cette fonction est justement Switch/Switch 2, où une différence de libellé entre RAWG et IGDB peut
 * refléter une vraie différence de plateforme et pas seulement une reformulation (ex. "Nintendo
 * Switch" contient "Switch" comme sous-chaîne, mais "Nintendo Switch 2" aussi — une correspondance
 * approximative ferait glisser une case "joué sur Switch" vers "Switch 2" à tort). Sans
 * correspondance exacte fiable pour une plateforme cochée, mieux vaut garder la donnée RAWG intacte
 * que de la remplacer par une valeur qui casse ou fausse silencieusement ce choix.
 */
fun realignPlayedPlatformsOrNull(playedPlatforms: Set<String>, newPlatform: String): Set<String>? {
    if (playedPlatforms.isEmpty()) return emptySet()
    val newPlatforms = parsePlatforms(newPlatform)
    return playedPlatforms.map { old ->
        newPlatforms.firstOrNull { it.equals(old, ignoreCase = true) } ?: return null
    }.toSet()
}
