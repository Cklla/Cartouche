package fr.cklla.cartouche.domain.model

/**
 * Statuts possibles d'un jeu dans le backlog.
 *
 * Ces quatre statuts sont figés par la maquette (Maquettes/design_handoff_backlog_jeux) :
 * chacun a sa propre couleur dans la charte graphique, il n'y a pas de 5e statut prévu.
 */
enum class GameStatus {
    A_FAIRE,
    EN_COURS,
    TERMINE,
    ABANDONNE,
}
