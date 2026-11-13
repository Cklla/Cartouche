package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.IgdbLookup

/**
 * Lecture, auprès d'IGDB, des informations d'un jeu déjà connu de l'app : temps de jeu estimé,
 * plateformes, jaquette et année (voir `IgdbGameMatch`).
 *
 * Séparé de [GameRepository] : ce repository ne touche jamais au backlog local, il ne fait que
 * répondre à une question ponctuelle ("qu'est-ce qu'IGDB sait de ce jeu ?"). C'est au ViewModel
 * appelant (voir `DetailViewModel`) de décider quand l'interroger et d'enregistrer le résultat via
 * [GameRepository], en appliquant la règle d'enrichissement (`applyIgdbEnrichment`).
 */
interface IgdbPlaytimeRepository {

    /**
     * Lit ce qu'IGDB sait du jeu ([IgdbLookup.Found]), ou indique qu'il est introuvable
     * ([IgdbLookup.NotFound], réponse définitive) ou que la lecture a échoué ([IgdbLookup.Failed] :
     * réseau, authentification, quota...) — jamais d'exception qui remonterait jusqu'à l'UI.
     *
     * Avec un [igdbId] connu, le jeu est lu directement par id. Sans id, le jeu est rapproché par
     * nom normalisé et année de sortie ([title], [releaseYear]) ; l'id et la jaquette ne sont
     * retenus que si le rapprochement est certain (voir `IgdbPlaytimeRepositoryImpl`).
     */
    suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbLookup
}
