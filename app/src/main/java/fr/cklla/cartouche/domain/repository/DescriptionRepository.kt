package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.description.DescriptionView

/**
 * Point d'accès aux descriptions de jeux pour les ViewModels : texte d'IGDB et traduction française,
 * lus à l'ouverture d'une fiche, traduits quand on déroule la section. Le ViewModel ignore d'où viennent le texte (cache Room, serveur,
 * fonction de traduction).
 */
interface DescriptionRepository {

    /**
     * Lecture seule, à l'ouverture de la fiche : ce qu'on sait déjà de la description du jeu [gameId]
     * (voir `peekDescription`). Ne demande jamais de traduction, donc ne coûte rien. Ne lève jamais : une
     * lecture en panne donne `DescriptionView.Collapsed`.
     */
    suspend fun peek(gameId: String, igdbId: Long?): DescriptionView

    /**
     * Ce que la fiche du jeu [gameId], relié à l'id IGDB [igdbId], doit montrer une fois la section
     * déroulée. Applique la règle de déclenchement (voir `resolveDescription`) : au plus une demande de traduction par appel, jamais
     * de boucle, une panne ne consomme pas l'essai. Ne lève jamais : toute panne est un
     * `DescriptionView.Failed`.
     */
    suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView
}
