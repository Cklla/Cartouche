package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.description.DescriptionView

/**
 * Point d'accès aux descriptions de jeux pour les ViewModels : texte d'IGDB et traduction française,
 * lus à l'ouverture d'une fiche. Le ViewModel ignore d'où viennent le texte (cache Room, serveur,
 * fonction de traduction).
 */
interface DescriptionRepository {

    /**
     * Ce que la fiche du jeu [gameId], relié à l'id IGDB [igdbId], doit montrer. Applique la règle de
     * déclenchement (voir `resolveDescription`) : au plus une demande de traduction par appel, jamais
     * de boucle, une panne ne consomme pas l'essai. Ne lève jamais : toute panne est un
     * `DescriptionView.Failed`.
     */
    suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView
}
