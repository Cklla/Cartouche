package fr.cklla.cartouche.domain.model

/**
 * Représente un jeu du backlog, tel que manipulé par l'UI et les ViewModels.
 *
 * C'est le modèle "métier" : il ne dépend ni de Room (entité base de données)
 * ni de Firestore, ni de RAWG (réponse API). Le Repository fait la conversion
 * entre ces représentations et ce modèle.
 *
 * @param id identifiant du jeu (UUID généré à la création) — chaîne vide si pas encore persisté.
 *   Sert aussi d'identifiant de document Firestore, pour que le même id désigne le même jeu sur
 *   Room et sur le cloud sans table de correspondance séparée.
 * @param rawgId identifiant RAWG du jeu, ou `null` pour un jeu ajouté avant l'introduction de ce
 *   champ. Sert uniquement à retrouver la fiche Steam du jeu (voir `IgdbPlaytimeRepository`,
 *   étape 1 de la correspondance IGDB par ID Steam) — jamais affiché ni utilisé ailleurs.
 * @param releaseYear année de sortie RAWG, ou `null` si inconnue. Sert de signal secondaire pour
 *   départager plusieurs candidats IGDB proches par le nom (voir `IgdbPlaytimeRepository`, étape 2).
 * @param userPlaytimeHours temps de jeu renseigné manuellement par l'utilisateur, en heures.
 * @param estimatedPlaytimeHastilyHours temps pour "rusher" le jeu selon IGDB
 *   (`game_time_to_beats.hastily`), en heures ; `null` si IGDB n'a pas cette donnée pour ce jeu.
 * @param estimatedPlaytimeNormallyHours temps pour terminer le jeu "normalement" selon IGDB
 *   (`game_time_to_beats.normally`), en heures ; `null` si IGDB n'a pas cette donnée pour ce jeu.
 * @param estimatedPlaytimeCompletelyHours temps pour terminer le jeu "à 100%" selon IGDB
 *   (`game_time_to_beats.completely`), en heures ; `null` si IGDB n'a pas cette donnée pour ce jeu.
 *
 * Les trois champs `estimatedPlaytime*` sont indépendamment nullables : IGDB peut n'avoir que
 * certaines des trois durées pour un jeu donné (voir `DetailScreen`, qui n'affiche que celles
 * renseignées). Tous les trois valent `null` tant que la recherche IGDB n'a pas encore eu lieu
 * (voir `DetailViewModel`, qui la déclenche à l'ouverture de la fiche détail et met le résultat
 * en cache ici) ; aucun n'est jamais modifiable par l'utilisateur, contrairement à
 * [userPlaytimeHours].
 * @param rating note personnelle de 1 à 5, ou null si le jeu n'est pas encore noté.
 * @param completedAt date (epoch millis) à laquelle le jeu est passé au statut [GameStatus.TERMINE],
 *   ou `null` si le jeu n'est pas terminé (ou l'était déjà avant l'introduction de ce champ).
 *   Dérivé automatiquement par le Repository à chaque transition de statut, jamais renseigné par
 *   l'UI — sert au filtre par année de complétion (Bibliothèque et Stats).
 * @param abandonedAt même mécanique que [completedAt], mais pour le passage au statut
 *   [GameStatus.ABANDONNE] — sert au filtre par année d'abandon (Bibliothèque et Stats).
 */
data class Game(
    val id: String = "",
    val title: String,
    val platform: String,
    val genre: String,
    val status: GameStatus,
    val rawgId: Long? = null,
    val releaseYear: Int? = null,
    val userPlaytimeHours: Int = 0,
    val estimatedPlaytimeHastilyHours: Int? = null,
    val estimatedPlaytimeNormallyHours: Int? = null,
    val estimatedPlaytimeCompletelyHours: Int? = null,
    val rating: Int? = null,
    val notes: String = "",
    val coverUrl: String? = null,
    val completedAt: Long? = null,
    val abandonedAt: Long? = null,
)
