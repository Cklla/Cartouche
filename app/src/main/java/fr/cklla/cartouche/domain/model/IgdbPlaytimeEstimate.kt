package fr.cklla.cartouche.domain.model

/**
 * Résultat d'une recherche IGDB réussie (correspondance de titre trouvée, endpoint
 * `game_time_to_beats` interrogé avec succès).
 *
 * Chaque champ est indépendamment nullable : IGDB peut n'avoir renseigné qu'une partie des trois
 * durées pour un jeu donné (voir `DetailScreen`, qui n'affiche que celles présentes). Ce type est
 * `null` dans son ensemble (voir `IgdbPlaytimeRepository`) uniquement quand la recherche elle-même
 * a échoué (pas de correspondance, erreur réseau...) — jamais quand elle a réussi mais que les
 * trois champs sont vides.
 */
data class IgdbPlaytimeEstimate(
    val hastilyHours: Int?,
    val normallyHours: Int?,
    val completelyHours: Int?,
)
