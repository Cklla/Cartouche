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
