package fr.cklla.cartouche.domain.model

/**
 * Représente un jeu du backlog, tel que manipulé par l'UI et les ViewModels.
 *
 * C'est le modèle "métier" : il ne dépend ni de Room (entité base de données)
 * ni de Firestore, ni de RAWG (réponse API). Le Repository fait la conversion
 * entre ces représentations et ce modèle.
 *
 * @param id identifiant local (0 = jeu pas encore persisté).
 * @param rating note personnelle de 1 à 5, ou null si le jeu n'est pas encore noté.
 */
data class Game(
    val id: Long = 0L,
    val title: String,
    val platform: String,
    val genre: String,
    val status: GameStatus,
    val hoursPlayed: Int = 0,
    val rating: Int? = null,
    val notes: String = "",
    val coverUrl: String? = null,
)
