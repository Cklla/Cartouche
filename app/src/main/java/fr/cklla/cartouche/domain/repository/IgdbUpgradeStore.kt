package fr.cklla.cartouche.domain.repository

/**
 * Mémorise, par jeu et sur cet appareil, qu'une remise à niveau IGDB (règle d'enrichissement) a déjà
 * été tentée (réponse d'IGDB obtenue, jeu trouvé ou non) pour un jeu **sans id IGDB** : ces jeux ont pu passer par l'ancienne correspondance
 * (`Game.igdbLookupAttempted` déjà à `true`) sans jamais recevoir d'id ni de jaquette IGDB, et
 * sont donc relancés **une seule fois**.
 *
 * Mémoire locale plutôt que colonne : la synchro remplace chaque ligne locale par celle du serveur,
 * ce qui effacerait un drapeau stocké dans Room. Aucune donnée n'est envoyée au serveur.
 */
interface IgdbUpgradeStore {
    fun isDone(gameId: String): Boolean
    fun markDone(gameId: String)
}
