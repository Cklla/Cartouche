package fr.cklla.cartouche.domain.model

/**
 * Représente un jeu du backlog, tel que manipulé par l'UI et les ViewModels.
 *
 * C'est le modèle "métier" : il ne dépend ni de Room (entité base de données)
 * ni de Firestore, ni de RAWG (réponse API). Le Repository fait la conversion
 * entre ces représentations et ce modèle.
 *
 * @param id identifiant local (0 = jeu pas encore persisté).
 * @param userPlaytimeHours temps de jeu renseigné manuellement par l'utilisateur, en heures.
 * @param estimatedPlaytimeHours temps nécessaire pour "terminer normalement" le jeu selon IGDB
 *   (`game_time_to_beats.normally`), en heures ; `null` si IGDB n'a pas cette donnée ou si la
 *   recherche n'a pas encore eu lieu (voir `DetailViewModel`, qui déclenche la recherche IGDB à
 *   l'ouverture de la fiche détail et met le résultat en cache ici). Distinct de
 *   [userPlaytimeHours], jamais modifiable par l'utilisateur.
 * @param rating note personnelle de 1 à 5, ou null si le jeu n'est pas encore noté.
 */
data class Game(
    val id: Long = 0L,
    val title: String,
    val platform: String,
    val genre: String,
    val status: GameStatus,
    val userPlaytimeHours: Int = 0,
    val estimatedPlaytimeHours: Int? = null,
    val rating: Int? = null,
    val notes: String = "",
    val coverUrl: String? = null,
)
