package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.IgdbGameMatch

/**
 * Lecture, auprès d'IGDB, des informations d'un jeu déjà connu de l'app : temps de jeu estimé,
 * plateformes, jaquette et année (voir [IgdbGameMatch]).
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local, il ne fait que
 * répondre à une question ponctuelle ("qu'est-ce qu'IGDB sait de ce jeu ?"). C'est au ViewModel
 * appelant (voir `DetailViewModel`) de décider quand l'interroger et d'enregistrer le résultat via
 * [GameRepository], en appliquant la règle d'enrichissement (`applyIgdbEnrichment`).
 */
interface IgdbPlaytimeRepository {

    /**
     * Renvoie ce qu'IGDB sait du jeu, ou `null` si le jeu n'est pas identifié ou en cas d'échec
     * (réseau, authentification, quota...) — jamais d'exception qui remonterait jusqu'à l'UI.
     *
     * Avec un [igdbId] connu, le jeu est lu directement par id. Sans id, la correspondance se fait
     * par nom normalisé et année de sortie ([title], [releaseYear]) ; faute de correspondance
     * fiable, le résultat est `null`.
     */
    suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbGameMatch?
}
